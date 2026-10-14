package co.ferreteria.inventario.flujos;

import co.ferreteria.inventario.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF de compras y ventas: entradas, salidas con conversión, FEFO, garantía, saldos negativos y anulaciones. */
class CompraVentaTest extends PruebaIntegracion {

    private static final String TORNILLO = "2990000000095";   // UND, caja x 100, sin vencimiento
    private static final String PUNTILLA = "2990000000101";   // KG, fracción 0,25, se vende por libra (0,5 kg)
    private static final String CABLE = "2990000000088";      // M, fracción 0,5
    private static final String CEMENTO = "2990000000057";    // KG, controla vencimiento (lote)
    private static final String TALADRO = "2990000000071";    // garantía 36 meses

    private Map<String, Object> linea(String barras, String unidad, String cantidad) {
        return Map.of("referenciaId", referencia(barras), "unidad", unidad, "cantidad", new BigDecimal(cantidad));
    }

    private JsonNode vender(String login, List<Map<String, Object>> lineas) throws Exception {
        return leer(enviar(login, "/api/ventas", Map.of("uuid", UUID.randomUUID(), "formaPago", "CONTADO", "lineas", lineas))
                .andExpect(status().isCreated()));
    }

    @Test
    void compraEntraAlInventarioConDocumentoDeIngresoYRecalculaCostoYPrecio() throws Exception {
        BigDecimal antes = saldo(TORNILLO, "DISPONIBLE");
        var r = enviar("compras1", "/api/compras", Map.of(
                "uuid", UUID.randomUUID(), "proveedorId", tercero("900123456"), "numeroFacturaProveedor", "FV-T1",
                "fechaFacturaProveedor", LocalDate.now().toString(),
                "lineas", List.of(Map.of("referenciaId", referencia(TORNILLO), "unidad", "CAJA", "cantidad", 2, "costoUnitario", 9000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value(org.hamcrest.Matchers.startsWith("ING-")));
        assertThat(saldo(TORNILLO, "DISPONIBLE")).isEqualByComparingTo(antes.add(new BigDecimal("200")));

        var ref = jdbc.sql("SELECT ultimo_costo, precio_venta, porcentaje_incremento FROM referencia WHERE codigo_barras = :c")
                .param("c", TORNILLO).query().singleRow();
        assertThat((BigDecimal) ref.get("ultimo_costo")).isEqualByComparingTo("90");       // 9000 / 100 unidades
        assertThat((BigDecimal) ref.get("precio_venta")).isEqualByComparingTo("150");      // 90 × 1,5 → múltiplo de 50
        assertThat(leer(r).get("id").asLong()).isPositive();
    }

    @Test
    void ventaConvierteUnidadesUsaLoteFefoYCalculaGarantia() throws Exception {
        BigDecimal puntilla = saldo(PUNTILLA, "DISPONIBLE");
        BigDecimal cemento = saldo(CEMENTO, "DISPONIBLE");
        JsonNode r = vender("vendedor1", List.of(linea(PUNTILLA, "LB", "3"), linea(CEMENTO, "BULTO", "1"), linea(TALADRO, "UND", "1")));
        assertThat(r.get("factura").asText()).startsWith("FE-");

        assertThat(saldo(PUNTILLA, "DISPONIBLE")).isEqualByComparingTo(puntilla.subtract(new BigDecimal("1.5")));  // 3 lb = 1,5 kg
        assertThat(saldo(CEMENTO, "DISPONIBLE")).isEqualByComparingTo(cemento.subtract(new BigDecimal("50")));

        JsonNode detalle = leer(consultar("vendedor1", "/api/ventas/" + r.get("id").asLong()).andExpect(status().isOk()));
        JsonNode lineas = detalle.get("lineas");
        assertThat(lineas).hasSize(3);
        for (JsonNode l : lineas) {
            if (l.get("referencia").asText().contains("Argos")) assertThat(l.get("codigo_lote").asText()).isEqualTo("AR2609");
            if (l.get("referencia").asText().contains("DeWalt"))
                assertThat(l.get("fecha_fin_garantia").asText()).isEqualTo(LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusMonths(36).toString());
        }
    }

    @Test
    void noPermiteVenderMasDeLoDisponible() throws Exception {
        BigDecimal antes = saldo(TALADRO, "DISPONIBLE");
        enviar("vendedor1", "/api/ventas", Map.of("formaPago", "CONTADO",
                "lineas", List.of(linea(TALADRO, "UND", antes.add(BigDecimal.TEN).toPlainString()))))
                .andExpect(status().isConflict());
        assertThat(saldo(TALADRO, "DISPONIBLE")).isEqualByComparingTo(antes);
    }

    @Test
    void rechazaFraccionQueNoEsMultiploDeLaMinima() throws Exception {
        enviar("vendedor1", "/api/ventas", Map.of("formaPago", "CONTADO", "lineas", List.of(linea(CABLE, "M", "0.3"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarPrecioEnLaVentaRequierePermiso() throws Exception {
        var l = Map.<String, Object>of("referenciaId", referencia(TORNILLO), "unidad", "UND", "cantidad", 1, "precioUnitario", 1);
        enviar("vendedor1", "/api/ventas", Map.of("formaPago", "CONTADO", "lineas", List.of(l)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anularVentaDevuelveLasExistenciasYEsIdempotentePorUuid() throws Exception {
        BigDecimal antes = saldo(TORNILLO, "DISPONIBLE");
        UUID uuid = UUID.randomUUID();
        Map<String, Object> venta = Map.of("uuid", uuid, "formaPago", "CONTADO", "lineas", List.of(linea(TORNILLO, "UND", "10")));
        long id = leer(enviar("vendedor1", "/api/ventas", venta).andExpect(status().isCreated())).get("id").asLong();
        // Reenviar la misma venta (por ejemplo, tras un corte de red) no la duplica.
        long otra = leer(enviar("vendedor1", "/api/ventas", venta)).get("id").asLong();
        assertThat(otra).isEqualTo(id);
        assertThat(saldo(TORNILLO, "DISPONIBLE")).isEqualByComparingTo(antes.subtract(BigDecimal.TEN));

        enviar("vendedor1", "/api/ventas/" + id + "/anular", Map.of("motivo", "prueba")).andExpect(status().isForbidden());
        enviar("admin", "/api/ventas/" + id + "/anular", Map.of("motivo", "Cliente desistió")).andExpect(status().isOk());
        assertThat(saldo(TORNILLO, "DISPONIBLE")).isEqualByComparingTo(antes);
        assertThat(jdbc.sql("SELECT estado FROM venta WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("ANULADA");
    }

    @Test
    void cadaMovimientoQuedaAuditadoConUsuario() throws Exception {
        long id = vender("vendedor1", List.of(linea(TORNILLO, "UND", "1"))).get("id").asLong();
        String usuario = jdbc.sql("""
                        SELECT u.login FROM auditoria a JOIN usuario u ON u.id = a.usuario_id
                         WHERE a.tabla = 'venta' AND a.registro_id = :id AND a.accion = 'INSERT'""")
                .param("id", String.valueOf(id)).query(String.class).single();
        assertThat(usuario).isEqualTo("vendedor1");
    }
}
