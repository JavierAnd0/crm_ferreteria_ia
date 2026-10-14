package co.ferreteria.inventario.inventario;

import co.ferreteria.inventario.comun.ContextoAuditoria;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Único punto de escritura del kárdex. El disparador fn_aplicar_movimiento valida período,
 * lote, vencimiento y saldo (sin negativos), y actualiza existencias y costo promedio en la
 * misma transacción.
 */
@Component
public class Kardex {

    public record Registrado(long id, BigDecimal costoUnitarioBase, BigDecimal saldoTotalDespues) {}

    private final JdbcClient jdbc;

    public Kardex(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Registrado registrar(Movimiento m, Short dispositivoId) {
        return jdbc.sql("""
                        INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_origen, estado_destino,
                                                cantidad_base, costo_unitario_base, documento_tipo, documento_id, documento_detalle_id,
                                                reversa_movimiento_id, usuario_id, dispositivo_id, origen)
                        VALUES (:fecha, :uuid, CAST(:tipo AS tipo_movimiento), :ref, :lote, CAST(:origen AS estado_inventario),
                                CAST(:destino AS estado_inventario), :cant, :costo, :docTipo, :docId, :detId, :reversa, :usuario,
                                :dispositivo, CAST(:origenOp AS origen_operacion))
                        RETURNING id, costo_unitario_base, saldo_total_despues""")
                .param("fecha", m.fecha())
                .param("uuid", m.syncUuid() == null ? UUID.randomUUID() : m.syncUuid())
                .param("tipo", m.tipo())
                .param("ref", m.referenciaId())
                .param("lote", m.loteId())
                .param("origen", m.estadoOrigen())
                .param("destino", m.estadoDestino())
                .param("cant", m.cantidadBase())
                .param("costo", m.costoUnitarioBase() == null ? BigDecimal.ZERO : m.costoUnitarioBase())
                .param("docTipo", m.documentoTipo())
                .param("docId", m.documentoId())
                .param("detId", m.documentoDetalleId())
                .param("reversa", m.reversaId())
                .param("usuario", UsuarioActual.id())
                .param("dispositivo", dispositivoId)
                .param("origenOp", ContextoAuditoria.origenActual())
                .query((rs, n) -> new Registrado(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)))
                .single();
    }
}
