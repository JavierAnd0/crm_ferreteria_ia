package co.ferreteria.inventario.flujos;

import co.ferreteria.inventario.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Operación sin internet: bloque de numeración por equipo, envío idempotente y conflictos sin saldos negativos. */
class SincronizacionTest extends PruebaIntegracion {

    private static final String SILICONA = "2990000000040";

    private short caja() {
        return jdbc.sql("SELECT id FROM dispositivo WHERE nombre = 'CAJA-01'").query(Short.class).single();
    }

    private Map<String, Object> operacion(UUID uuid, JsonNode bloque, long numero, String cantidad) {
        return Map.of("uuid", uuid, "dispositivoId", caja(), "prefijo", bloque.get("prefijo").asText(), "numero", numero,
                "creadoLocalEn", OffsetDateTime.now().minusMinutes(5).toString(),
                "venta", Map.of("uuid", uuid, "formaPago", "CONTADO", "lineas",
                        List.of(Map.of("referenciaId", referencia(SILICONA), "unidad", "UND", "cantidad", new BigDecimal(cantidad)))));
    }

    @Test
    void ventaSinConexionSeAplicaUnaSolaVezAunqueSeReenvie() throws Exception {
        JsonNode bloque = leer(enviar("vendedor1", "/api/sync/dispositivos/" + caja() + "/bloque?cantidad=5", Map.of())
                .andExpect(status().isOk()));
        long numero = bloque.get("desde").asLong();
        BigDecimal antes = saldo(SILICONA, "DISPONIBLE");
        UUID uuid = UUID.randomUUID();

        JsonNode r1 = leer(enviar("vendedor1", "/api/sync/ventas", List.of(operacion(uuid, bloque, numero, "2"))).andExpect(status().isOk()));
        JsonNode r2 = leer(enviar("vendedor1", "/api/sync/ventas", List.of(operacion(uuid, bloque, numero, "2"))).andExpect(status().isOk()));

        assertThat(r1.get(0).get("estado").asText()).isEqualTo("APLICADA");
        assertThat(r2.get(0).get("factura").asText()).isEqualTo(r1.get(0).get("factura").asText());
        assertThat(saldo(SILICONA, "DISPONIBLE")).isEqualByComparingTo(antes.subtract(new BigDecimal("2")));
        assertThat(jdbc.sql("SELECT origen::text FROM venta WHERE uuid = :u").param("u", uuid).query(String.class).single())
                .isEqualTo("FUERA_DE_LINEA");
    }

    @Test
    void siOtraCajaVendioLaExistenciaQuedaEnConflictoSinNegativos() throws Exception {
        JsonNode bloque = leer(enviar("vendedor1", "/api/sync/dispositivos/" + caja() + "/bloque?cantidad=1", Map.of()));
        BigDecimal antes = saldo(SILICONA, "DISPONIBLE");
        UUID uuid = UUID.randomUUID();
        JsonNode r = leer(enviar("vendedor1", "/api/sync/ventas",
                List.of(operacion(uuid, bloque, bloque.get("desde").asLong(), antes.add(BigDecimal.ONE).toPlainString()))));

        assertThat(r.get(0).get("estado").asText()).isEqualTo("CONFLICTO");
        assertThat(saldo(SILICONA, "DISPONIBLE")).isEqualByComparingTo(antes);
        assertThat(leer(consultar("admin", "/api/sync/conflictos")).findValuesAsText("uuid")).contains(uuid.toString());

        enviar("admin", "/api/sync/conflictos/" + uuid + "/resolver", Map.of("resolucion", "Se anuló la factura en papel")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT estado FROM operacion_fuera_linea WHERE uuid = :u").param("u", uuid).query(String.class).single())
                .isEqualTo("RECHAZADA");
    }

    @Test
    void equipoNoAutorizadoNoRecibeNumeracion() throws Exception {
        short bodega = jdbc.sql("SELECT id FROM dispositivo WHERE nombre = 'BODEGA-01'").query(Short.class).single();
        enviar("vendedor1", "/api/sync/dispositivos/" + bodega + "/bloque", Map.of()).andExpect(status().isUnprocessableEntity());
    }
}
