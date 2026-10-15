package co.ferreteria.inventario.catalogo;

import co.ferreteria.inventario.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF-PRO-001/003: el resumen de una referencia trae su producto para poder agregarle otra presentación. */
class CatalogoTest extends PruebaIntegracion {

    private static final String TORNILLO = "2990000000095";

    @Test
    void agregaOtraPresentacionAlProductoDeUnaReferencia() throws Exception {
        JsonNode tornillo = leer(consultar("inventario1", "/api/referencias/codigo/" + TORNILLO).andExpect(status().isOk()));
        int productoId = tornillo.get("productoId").asInt();
        assertThat(productoId).isPositive();

        String codigo = "29" + (System.nanoTime() % 100_000_000_000L);
        Integer marca = jdbc.sql("SELECT id FROM marca ORDER BY id LIMIT 1").query(Integer.class).single();
        enviar("inventario1", "/api/productos/" + productoId + "/referencias", Map.of(
                "marcaId", marca, "presentacion", "Caja x 500", "codigoBarras", codigo, "unidadBase", "UND",
                "permiteFraccion", false, "fraccionMinima", 1, "tarifa", "IVA19", "porcentajeIncremento", 40,
                "conversiones", List.of())).andExpect(status().isCreated());

        JsonNode nueva = leer(consultar("vendedor1", "/api/referencias/codigo/" + codigo).andExpect(status().isOk()));
        assertThat(nueva.get("productoId").asInt()).isEqualTo(productoId);
        assertThat(nueva.get("producto").asText()).isEqualTo(tornillo.get("producto").asText());
    }

    @Test
    void sinPermisoNoSeAgreganPresentaciones() throws Exception {
        enviar("vendedor1", "/api/productos/1/referencias", Map.of("marcaId", 1, "presentacion", "x", "codigoBarras", "123456",
                "unidadBase", "UND")).andExpect(status().isForbidden());
    }
}
