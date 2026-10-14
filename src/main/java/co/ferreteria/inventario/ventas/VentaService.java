package co.ferreteria.inventario.ventas;

import co.ferreteria.inventario.comun.*;
import co.ferreteria.inventario.integracion.BandejaSalida;
import co.ferreteria.inventario.inventario.*;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * RF-VEN-001: registrar una venta y emitir su factura en una sola transacción. Cada línea genera
 * una salida del kárdex; la base de datos rechaza saldos negativos, lotes vencidos y meses cerrados.
 */
@Service
public class VentaService {

    public record LineaVenta(@NotNull Integer referenciaId, String unidad, @NotNull @Positive BigDecimal cantidad,
                             Long loteId, Long reservaId, @PositiveOrZero BigDecimal precioUnitario,
                             @PositiveOrZero BigDecimal descuento) {}

    public record NuevaVenta(UUID uuid, Long clienteId, @Pattern(regexp = "CONTADO|CREDITO") String formaPago,
                             @NotEmpty List<@Valid LineaVenta> lineas) {}

    public record Resultado(long id, String factura, BigDecimal subtotal, BigDecimal descuento, BigDecimal impuestos,
                            BigDecimal total) {}

    private static final String CONSUMIDOR_FINAL = "222222222222";

    private final JdbcClient jdbc;
    private final Kardex kardex;
    private final Consecutivos consecutivos;
    private final InfoReferencias referencias;
    private final BandejaSalida bandeja;

    public VentaService(JdbcClient jdbc, Kardex kardex, Consecutivos consecutivos, InfoReferencias referencias, BandejaSalida bandeja) {
        this.jdbc = jdbc;
        this.kardex = kardex;
        this.consecutivos = consecutivos;
        this.referencias = referencias;
        this.bandeja = bandeja;
    }

    /** Venta en línea: numeración del servidor y fecha actual. */
    @Transactional
    public Resultado emitir(NuevaVenta v, Short dispositivoId) {
        UUID uuid = v.uuid() == null ? UUID.randomUUID() : v.uuid();
        Optional<Resultado> previa = porUuid(uuid);
        if (previa.isPresent()) return previa.get();
        return crear(v, uuid, consecutivos.siguiente("FACTURA"), Fechas.ahora(), dispositivoId);
    }

    /** Venta hecha sin internet: número del bloque del equipo y fecha local del equipo (RF-OFF-001). */
    @Transactional
    public Resultado emitirFueraDeLinea(NuevaVenta v, short dispositivoId, String prefijo, long numero, OffsetDateTime fechaLocal) {
        UUID uuid = Objects.requireNonNull(v.uuid(), "uuid obligatorio");
        Optional<Resultado> previa = porUuid(uuid);
        if (previa.isPresent()) return previa.get();
        if (!consecutivos.numeroEntregadoAlEquipo(dispositivoId, prefijo, numero))
            throw new NegocioException("La factura " + prefijo + "-" + numero + " no pertenece a un bloque entregado al equipo");
        return crear(v, uuid, new Consecutivos.Numero(prefijo, numero), fechaLocal, dispositivoId);
    }

    private Resultado crear(NuevaVenta v, UUID uuid, Consecutivos.Numero numero, OffsetDateTime fecha, Short dispositivoId) {
        long clienteId = v.clienteId() != null ? v.clienteId() : consumidorFinal();
        Boolean esCliente = jdbc.sql("SELECT es_cliente AND activo FROM tercero WHERE id = :id").param("id", clienteId)
                .query(Boolean.class).optional().orElseThrow(() -> new NoEncontradoException("Cliente", clienteId));
        if (!esCliente) throw new NegocioException("El tercero " + clienteId + " no es un cliente activo");

        long ventaId = jdbc.sql("""
                        INSERT INTO venta (uuid, prefijo, numero_factura, cliente_id, fecha_emision, forma_pago, estado,
                                           estado_envio_fe, usuario_id, dispositivo_id, origen)
                        VALUES (:uuid, :prefijo, :numero, :cliente, :fecha, :pago, 'EMITIDA', 'PENDIENTE', :usuario, :disp,
                                CAST(:origen AS origen_operacion))
                        RETURNING id""")
                .param("uuid", uuid).param("prefijo", numero.prefijo()).param("numero", numero.numero())
                .param("cliente", clienteId).param("fecha", fecha).param("pago", v.formaPago() == null ? "CONTADO" : v.formaPago())
                .param("usuario", UsuarioActual.id()).param("disp", dispositivoId).param("origen", ContextoAuditoria.origenActual())
                .query(Long.class).single();

        BigDecimal subtotal = BigDecimal.ZERO, descuentos = BigDecimal.ZERO, impuestos = BigDecimal.ZERO;
        LocalDate dia = fecha.atZoneSameInstant(Fechas.ZONA).toLocalDate();
        short numLinea = 0;
        for (LineaVenta l : v.lineas()) {
            numLinea++;
            InfoReferencias.Info info = referencias.info(l.referenciaId());
            if (!info.activo()) throw new NegocioException("La referencia " + info.nombre() + " está inactiva");
            InfoReferencias.Unidad unidad = referencias.unidad(l.referenciaId(), l.unidad(), true);
            BigDecimal cantidadBase = Cantidades.aBase(l.cantidad(), unidad.factor());
            if (!Cantidades.esMultiplo(cantidadBase, info.fraccionMinima()))
                throw new NegocioException("La cantidad de " + info.nombre() + " debe ser múltiplo de " + info.fraccionMinima().stripTrailingZeros().toPlainString());

            BigDecimal precioLista = info.precioVenta().multiply(unidad.factor()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precio = l.precioUnitario() == null ? precioLista : l.precioUnitario().setScale(2, RoundingMode.HALF_UP);
            BigDecimal descuento = l.descuento() == null ? BigDecimal.ZERO : l.descuento().setScale(2, RoundingMode.HALF_UP);
            if ((precio.compareTo(precioLista) != 0 || descuento.signum() > 0) && !UsuarioActual.tienePermiso("PRECIO_CAMBIAR"))
                throw new NegocioException("Cambiar el precio o aplicar descuentos requiere el permiso PRECIO_CAMBIAR");
            if (precio.signum() == 0) throw new NegocioException("La referencia " + info.nombre() + " no tiene precio de venta");

            String estadoOrigen = "DISPONIBLE";
            Long loteId = l.loteId();
            if (l.reservaId() != null) {
                loteId = consumirReserva(l.reservaId(), clienteId, l.referenciaId(), cantidadBase, ventaId);
                estadoOrigen = "RESERVADA";
            } else if (info.controlaVencimiento() && loteId == null) {
                loteId = referencias.loteFefo(l.referenciaId(), "DISPONIBLE", cantidadBase, dia);
            }
            LocalDate finGarantia = info.mesesGarantia() == null ? null : dia.plusMonths(info.mesesGarantia());

            long detalleId = jdbc.sql("""
                            INSERT INTO venta_detalle (venta_id, linea, referencia_id, unidad_id, lote_id, cantidad, factor_a_base,
                                                       precio_unitario, valor_descuento, porcentaje_impuesto, fecha_fin_garantia)
                            VALUES (:venta, :linea, :ref, :unidad, :lote, :cant, :factor, :precio, :desc, :iva, :garantia)
                            RETURNING id""")
                    .param("venta", ventaId).param("linea", numLinea).param("ref", l.referenciaId())
                    .param("unidad", unidad.unidadId()).param("lote", loteId).param("cant", l.cantidad())
                    .param("factor", unidad.factor()).param("precio", precio).param("desc", descuento)
                    .param("iva", info.porcentajeIva()).param("garantia", finGarantia)
                    .query(Long.class).single();
            Kardex.Registrado mov = kardex.registrar(
                    Movimiento.salidaVenta(fecha, l.referenciaId(), loteId, estadoOrigen, cantidadBase, ventaId, detalleId), dispositivoId);
            jdbc.sql("UPDATE venta_detalle SET costo_unitario_base = :c WHERE id = :id")
                    .param("c", mov.costoUnitarioBase()).param("id", detalleId).update();

            BigDecimal bruto = l.cantidad().multiply(precio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal neto = bruto.subtract(descuento);
            if (neto.signum() < 0) throw new NegocioException("El descuento supera el valor de la línea");
            subtotal = subtotal.add(bruto);
            descuentos = descuentos.add(descuento);
            impuestos = impuestos.add(neto.multiply(info.porcentajeIva()).movePointLeft(2));
        }
        impuestos = impuestos.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(descuentos).add(impuestos);
        jdbc.sql("UPDATE venta SET subtotal = :s, valor_descuento = :d, valor_impuestos = :i, total = :t WHERE id = :id")
                .param("s", subtotal).param("d", descuentos).param("i", impuestos).param("t", total).param("id", ventaId).update();

        String factura = numero.prefijo() + "-" + numero.numero();
        bandeja.registrar("VENTA_EMITIDA", "VENTA", ventaId, Map.of("factura", factura, "uuid", uuid, "clienteId", clienteId,
                "total", total, "fecha", fecha.toString()));
        return new Resultado(ventaId, factura, subtotal, descuentos, impuestos, total);
    }

    /** Anula una factura: cada salida vuelve a DISPONIBLE mediante un movimiento ANULACION_VENTA. */
    @Transactional
    public void anular(long ventaId, String motivo, Short dispositivoId) {
        String estado = jdbc.sql("SELECT estado FROM venta WHERE id = :id FOR UPDATE").param("id", ventaId)
                .query(String.class).optional().orElseThrow(() -> new NoEncontradoException("Venta", ventaId));
        if (!"EMITIDA".equals(estado)) throw new NegocioException("Solo se anulan facturas emitidas");
        OffsetDateTime ahora = Fechas.ahora();
        jdbc.sql("""
                        SELECT id, referencia_id, lote_id, cantidad_base, costo_unitario_base, documento_detalle_id
                          FROM movimiento WHERE documento_tipo = 'VENTA' AND documento_id = :id AND tipo = 'SALIDA_VENTA'""")
                .param("id", ventaId)
                .query((rs, n) -> new Movimiento(ahora, "ANULACION_VENTA", rs.getInt(2), (Long) rs.getObject(3), null, "DISPONIBLE",
                        rs.getBigDecimal(4), rs.getBigDecimal(5), "VENTA", ventaId, rs.getLong(6), rs.getLong(1), null))
                .list()
                .forEach(m -> kardex.registrar(m, dispositivoId));
        jdbc.sql("UPDATE venta SET estado = 'ANULADA' WHERE id = :id").param("id", ventaId).update();
        bandeja.registrar("VENTA_ANULADA", "VENTA", ventaId, Map.of("motivo", motivo));
    }

    private Long consumirReserva(long reservaId, long clienteId, int referenciaId, BigDecimal cantidadBase, long ventaId) {
        record Reserva(long clienteId, int referenciaId, Long loteId, BigDecimal cantidadBase, String estado) {}
        Reserva r = jdbc.sql("SELECT cliente_id, referencia_id, lote_id, cantidad_base, estado FROM reserva WHERE id = :id FOR UPDATE")
                .param("id", reservaId).query(Reserva.class).optional()
                .orElseThrow(() -> new NoEncontradoException("Reserva", reservaId));
        if (!"ACTIVA".equals(r.estado())) throw new NegocioException("La reserva " + reservaId + " no está activa");
        if (r.clienteId() != clienteId) throw new NegocioException("La reserva " + reservaId + " pertenece a otro cliente");
        if (r.referenciaId() != referenciaId || r.cantidadBase().compareTo(cantidadBase) != 0)
            throw new NegocioException("La línea debe vender exactamente la referencia y cantidad reservadas");
        jdbc.sql("UPDATE reserva SET estado = 'CONSUMIDA', venta_id = :v WHERE id = :id").param("v", ventaId).param("id", reservaId).update();
        return r.loteId();
    }

    private long consumidorFinal() {
        return jdbc.sql("SELECT id FROM tercero WHERE tipo_identificacion = 'CC' AND numero_identificacion = :n")
                .param("n", CONSUMIDOR_FINAL).query(Long.class).single();
    }

    private Optional<Resultado> porUuid(UUID uuid) {
        return jdbc.sql("SELECT id, prefijo || '-' || numero_factura, subtotal, valor_descuento, valor_impuestos, total FROM venta WHERE uuid = :u")
                .param("u", uuid)
                .query((rs, n) -> new Resultado(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6)))
                .optional();
    }
}
