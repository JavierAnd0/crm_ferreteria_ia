package co.ferreteria.inventario.flujos;

import co.ferreteria.inventario.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.ferreteria.inventario.inventario.TareasInventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Estados disponible / reservada / dañada, reservas que vencen y venta que consume una reserva. */
class EstadosYReservasTest extends PruebaIntegracion {

    private static final String BOMBILLO = "2990000000118";

    @Autowired TareasInventario tareas;

    private long reservar(String cantidad) throws Exception {
        return leer(enviar("vendedor1", "/api/reservas", Map.of("uuid", UUID.randomUUID(), "clienteId", tercero("901987654"),
                "referenciaId", referencia(BOMBILLO), "cantidadBase", new BigDecimal(cantidad))).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    @Test
    void reservarYLiberarMueveEntreEstadosSinCambiarElTotal() throws Exception {
        BigDecimal disp = saldo(BOMBILLO, "DISPONIBLE"), res = saldo(BOMBILLO, "RESERVADA");
        long id = reservar("4");
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo(disp.subtract(new BigDecimal("4")));
        assertThat(saldo(BOMBILLO, "RESERVADA")).isEqualByComparingTo(res.add(new BigDecimal("4")));

        enviar("vendedor1", "/api/reservas/" + id + "/liberar", Map.of()).andExpect(status().isOk());
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo(disp);
        assertThat(saldo(BOMBILLO, "RESERVADA")).isEqualByComparingTo(res);
    }

    @Test
    void ventaConsumeLaReservaDelCliente() throws Exception {
        BigDecimal disp = saldo(BOMBILLO, "DISPONIBLE"), res = saldo(BOMBILLO, "RESERVADA");
        long id = reservar("2");
        enviar("vendedor1", "/api/ventas", Map.of("clienteId", tercero("901987654"), "formaPago", "CREDITO", "lineas", List.of(
                Map.of("referenciaId", referencia(BOMBILLO), "unidad", "UND", "cantidad", 2, "reservaId", id))))
                .andExpect(status().isCreated());
        assertThat(saldo(BOMBILLO, "RESERVADA")).isEqualByComparingTo(res);
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo(disp.subtract(new BigDecimal("2")));
        assertThat(jdbc.sql("SELECT estado FROM reserva WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("CONSUMIDA");
    }

    @Test
    void reservasVencidasSeLiberanSolas() throws Exception {
        BigDecimal disp = saldo(BOMBILLO, "DISPONIBLE");
        long id = reservar("1");
        jdbc.sql("UPDATE reserva SET vence_en = now() - interval '1 minute' WHERE id = :id").param("id", id).update();
        tareas.liberarReservasVencidas();
        assertThat(jdbc.sql("SELECT estado FROM reserva WHERE id = :id").param("id", id).query(String.class).single()).isEqualTo("VENCIDA");
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo(disp);
    }

    @Test
    void loDanadoNoSePuedeVenderYSePuedeRecuperar() throws Exception {
        BigDecimal disp = saldo(BOMBILLO, "DISPONIBLE");
        Map<String, Object> cambio = Map.of("referenciaId", referencia(BOMBILLO), "cantidadBase", disp, "accion", "DANAR", "motivo", "Golpe");
        enviar("inventario1", "/api/existencias/cambios-estado", cambio).andExpect(status().isCreated());
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo("0");

        enviar("vendedor1", "/api/ventas", Map.of("formaPago", "CONTADO", "lineas",
                List.of(Map.of("referenciaId", referencia(BOMBILLO), "unidad", "UND", "cantidad", 1)))).andExpect(status().isConflict());

        enviar("inventario1", "/api/existencias/cambios-estado",
                Map.of("referenciaId", referencia(BOMBILLO), "cantidadBase", disp, "accion", "RECUPERAR", "motivo", "Revisado"))
                .andExpect(status().isCreated());
        assertThat(saldo(BOMBILLO, "DISPONIBLE")).isEqualByComparingTo(disp);
    }
}
