package co.ferreteria.inventario.flujos;

import co.ferreteria.inventario.PruebaIntegracion;
import co.ferreteria.inventario.comun.Fechas;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Migración desde Excel (validar, luego cargar) y cierres mensuales. */
class MigracionYCierreTest extends PruebaIntegracion {

    private static void fila(Sheet hoja, int n, String... valores) {
        Row r = hoja.createRow(n);
        for (int i = 0; i < valores.length; i++) r.createCell(i).setCellValue(valores[i]);
    }

    @Test
    void migraTercerosProductosYSaldosDesdeLaPlantilla() throws Exception {
        byte[] plantilla = mvc.perform(con("admin", get("/api/migracion/plantilla"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] archivo;
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla)); var salida = new ByteArrayOutputStream()) {
            // Se reemplazan las filas de ejemplo por datos nuevos y se agrega una fila con errores.
            fila(libro.getSheet("TERCEROS"), 1, "NATURAL", "CC", "79111222", "", "Pedro", "Gómez", "", "Calle 3", "Bogotá",
                    "3001112233", "pedro@correo.co", "S", "N");
            fila(libro.getSheet("TERCEROS"), 2, "JURIDICA", "NIT", "900123457", "1", "", "", "Sin dígito correcto", "", "", "", "", "N", "S");
            fila(libro.getSheet("PRODUCTOS"), 1, "MIG-LIJA-80", "Lija de agua 80", "Fijaciones", "N", "Genérica", "Pliego",
                    "2990000009991", "UND", "N", "1", "", "IVA19", "40", "20", "", "");
            fila(libro.getSheet("SALDOS"), 1, "2990000009991", "", "", "120", "1500");
            libro.write(salida);
            archivo = salida.toByteArray();
        }
        var subida = new MockMultipartFile("archivo", "migracion.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo);
        JsonNode resumen = leer(mvc.perform(con("admin", multipart("/api/migracion").file(subida)
                .param("fechaCorte", Fechas.hoy().toString()))).andExpect(status().isOk()));
        assertThat(resumen.get("total").asInt()).isEqualTo(4);
        assertThat(resumen.get("errores").asInt()).isEqualTo(1);
        // Validar no toca el inventario.
        assertThat(jdbc.sql("SELECT count(*) FROM referencia WHERE codigo_barras = '2990000009991'").query(Long.class).single()).isZero();

        long id = resumen.get("id").asLong();
        JsonNode cargado = leer(enviar("admin", "/api/migracion/" + id + "/cargar", Map.of()).andExpect(status().isOk()));
        assertThat(cargado.get("cargadas").asInt()).isEqualTo(3);
        assertThat(saldo("2990000009991", "DISPONIBLE")).isEqualByComparingTo("120");
        assertThat(jdbc.sql("SELECT precio_venta FROM referencia WHERE codigo_barras = '2990000009991'").query(java.math.BigDecimal.class).single())
                .isEqualByComparingTo("2100");   // 1500 × 1,4

        // El mismo archivo no se procesa dos veces.
        mvc.perform(con("admin", multipart("/api/migracion").file(subida).param("fechaCorte", Fechas.hoy().toString())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elMesEnCursoNoSePuedeCerrarYUnMesCerradoNoAdmiteMovimientos() throws Exception {
        enviar("admin", "/api/periodos/" + Fechas.periodo(Fechas.ahora()) + "/cerrar", Map.of()).andExpect(status().isUnprocessableEntity());

        // Septiembre de 2026 quedó cerrado en los datos de demostración: una compra con esa fecha se rechaza.
        enviar("compras1", "/api/compras", Map.of("proveedorId", tercero("900123456"), "numeroFacturaProveedor", "FV-SEP",
                "fechaFacturaProveedor", "2026-09-20", "fechaEfectiva", "2026-09-20T10:00:00-05:00",
                "lineas", List.of(Map.of("referenciaId", referencia("2990000000095"), "unidad", "UND", "cantidad", 1, "costoUnitario", 60))))
                .andExpect(status().isUnprocessableEntity());

        JsonNode saldos = leer(consultar("gerente1", "/api/periodos/202609/saldos").andExpect(status().isOk()));
        assertThat(saldos.size()).isEqualTo(2);
    }
}
