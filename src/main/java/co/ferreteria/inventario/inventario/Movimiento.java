package co.ferreteria.inventario.inventario;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Datos para registrar una línea del kárdex. Estados: DISPONIBLE, RESERVADA, DANADA. */
public record Movimiento(OffsetDateTime fecha, String tipo, int referenciaId, Long loteId, String estadoOrigen,
                         String estadoDestino, BigDecimal cantidadBase, BigDecimal costoUnitarioBase,
                         String documentoTipo, long documentoId, Long documentoDetalleId, Long reversaId,
                         UUID syncUuid) {

    public static Movimiento entradaCompra(OffsetDateTime fecha, int ref, Long lote, BigDecimal cantidad, BigDecimal costo,
                                           long compra, long detalle) {
        return new Movimiento(fecha, "ENTRADA_COMPRA", ref, lote, null, "DISPONIBLE", cantidad, costo, "COMPRA", compra, detalle, null, null);
    }

    public static Movimiento salidaVenta(OffsetDateTime fecha, int ref, Long lote, String origen, BigDecimal cantidad,
                                         long venta, long detalle) {
        return new Movimiento(fecha, "SALIDA_VENTA", ref, lote, origen, null, cantidad, BigDecimal.ZERO, "VENTA", venta, detalle, null, null);
    }

    public static Movimiento cambioEstado(OffsetDateTime fecha, int ref, Long lote, String origen, String destino,
                                          BigDecimal cantidad, String documentoTipo, long documentoId) {
        return new Movimiento(fecha, "CAMBIO_ESTADO", ref, lote, origen, destino, cantidad, BigDecimal.ZERO, documentoTipo, documentoId, null, null, null);
    }
}
