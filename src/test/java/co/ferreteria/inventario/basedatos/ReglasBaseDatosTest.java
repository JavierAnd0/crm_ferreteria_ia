package co.ferreteria.inventario.basedatos;

import co.ferreteria.inventario.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas que la base de datos garantiza aunque alguien escriba sin pasar por la aplicación.
 * Cada prueba corre en una transacción que se revierte al terminar.
 */
@Transactional
class ReglasBaseDatosTest extends PruebaIntegracion {

    private static final String MOV = """
            INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_origen, estado_destino,
                                    cantidad_base, costo_unitario_base, documento_tipo, documento_id, usuario_id)
            VALUES (CAST(:f AS timestamptz), gen_random_uuid(), CAST(:t AS tipo_movimiento), :r, :l,
                    CAST(:o AS estado_inventario), CAST(:d AS estado_inventario), :c, :costo, :doc, 0, 1)""";

    private void mover(String fecha, String tipo, int ref, Long lote, String origen, String destino, String cantidad, String costo) {
        jdbc.sql(MOV).param("f", fecha).param("t", tipo).param("r", ref).param("l", lote).param("o", origen).param("d", destino)
                .param("c", new BigDecimal(cantidad)).param("costo", new BigDecimal(costo))
                .param("doc", tipo.contains("COMPRA") ? "COMPRA" : tipo.contains("VENTA") ? "VENTA" : "CAMBIO_ESTADO").update();
    }

    /** Ejecuta dentro de un punto de guardado para que un error no aborte la transacción de la prueba. */
    private Throwable falla(Runnable accion) {
        jdbc.sql("SAVEPOINT prueba").update();
        try {
            accion.run();
            return null;
        } catch (RuntimeException e) {
            jdbc.sql("ROLLBACK TO SAVEPOINT prueba").update();
            return e;
        }
    }

    @Test
    void noPermiteSaldosNegativos() {
        int taladro = referencia("2990000000064");
        assertThat(falla(() -> mover("now", "SALIDA_VENTA", taladro, null, "DISPONIBLE", null, "1000", "0")))
                .hasMessageContaining("insuficiente");
    }

    @Test
    void costoPromedioPonderadoConCadaEntrada() {
        int taladro = referencia("2990000000064");   // saldo inicial: 3 a 280.000
        BigDecimal saldo = saldo("2990000000064", "DISPONIBLE");
        BigDecimal prom = jdbc.sql("SELECT costo_promedio FROM referencia WHERE id = :r").param("r", taladro).query(BigDecimal.class).single();
        mover("now", "ENTRADA_COMPRA", taladro, null, null, "DISPONIBLE", "1", "300000");
        BigDecimal esperado = saldo.multiply(prom).add(new BigDecimal("300000"))
                .divide(saldo.add(BigDecimal.ONE), 4, java.math.RoundingMode.HALF_UP);
        assertThat(jdbc.sql("SELECT costo_promedio FROM referencia WHERE id = :r").param("r", taladro).query(BigDecimal.class).single())
                .isEqualByComparingTo(esperado);
    }

    @Test
    void productoConVencimientoExigeLoteYNoSeVendeVencido() {
        int cemento = referencia("2990000000057");
        assertThat(falla(() -> mover("now", "ENTRADA_COMPRA", cemento, null, null, "DISPONIBLE", "10", "600")))
                .hasMessageContaining("requiere lote");
        long vencido = jdbc.sql("INSERT INTO lote (referencia_id, codigo_lote, fecha_vencimiento) VALUES (:r, 'VIEJO', DATE '2026-10-01') RETURNING id")
                .param("r", cemento).query(Long.class).single();
        mover("now", "ENTRADA_COMPRA", cemento, vencido, null, "DISPONIBLE", "10", "600");
        assertThat(falla(() -> mover("now", "SALIDA_VENTA", cemento, vencido, "DISPONIBLE", null, "1", "0")))
                .hasMessageContaining("vencido");
    }

    @Test
    void periodoCerradoNoAdmiteMovimientos() {
        assertThat(falla(() -> mover("2026-09-15 10:00-05", "ENTRADA_COMPRA", referencia("2990000000095"), null, null, "DISPONIBLE", "1", "60")))
                .hasMessageContaining("202609");
    }

    @Test
    void elKardexYLaAuditoriaSonInmutables() {
        assertThat(falla(() -> jdbc.sql("UPDATE movimiento SET cantidad_base = 1").update())).hasMessageContaining("inmutable");
        assertThat(falla(() -> jdbc.sql("DELETE FROM movimiento").update())).hasMessageContaining("inmutable");
        assertThat(falla(() -> jdbc.sql("DELETE FROM auditoria").update())).hasMessageContaining("inmutable");
    }

    @Test
    void noSeVendeLoDanado() {
        mover("now", "CAMBIO_ESTADO", referencia("2990000000095"), null, "DISPONIBLE", "DANADA", "1", "0");
        assertThat(falla(() -> mover("now", "SALIDA_VENTA", referencia("2990000000095"), null, "DANADA", null, "1", "0")))
                .hasMessageContaining("ck_mov_no_vender_danado");
    }

    @Test
    void unCodigoDeBarrasPorReferencia() {
        assertThat(falla(() -> jdbc.sql("UPDATE referencia SET codigo_barras = '2990000000019' WHERE codigo_barras = '2990000000026'").update()))
                .hasMessageContaining("duplicate key");
    }

    @Test
    void precioSeRedondeaHaciaArribaAMultiploDe50() {
        assertThat(jdbc.sql("SELECT fn_calcular_precio(11800, 40, 50)").query(BigDecimal.class).single()).isEqualByComparingTo("16550");
    }

    @Test
    void lasModificacionesQuedanAuditadasConElUsuarioDeLaSesion() {
        jdbc.sql("SELECT set_config('app.usuario_id', '4', true)").query().singleValue();
        jdbc.sql("UPDATE tercero SET telefono = '3009998877' WHERE numero_identificacion = '1012345678'").update();
        var fila = jdbc.sql("SELECT usuario_id, antes->>'telefono' AS antes, despues->>'telefono' AS despues FROM auditoria WHERE tabla = 'tercero' AND accion = 'UPDATE' ORDER BY id DESC LIMIT 1")
                .query().singleRow();
        assertThat(fila.get("usuario_id")).isEqualTo(4L);
        assertThat(fila.get("antes")).isEqualTo("3005550202");
        assertThat(fila.get("despues")).isEqualTo("3009998877");
    }
}
