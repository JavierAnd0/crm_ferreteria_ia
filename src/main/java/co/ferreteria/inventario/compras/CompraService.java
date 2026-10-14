package co.ferreteria.inventario.compras;

import co.ferreteria.inventario.comun.Cantidades;
import co.ferreteria.inventario.comun.Fechas;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
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
 * RF-COM-001: registrar y confirmar una compra en una sola transacción. Genera el documento de
 * ingreso (ING-n), una entrada al kárdex por línea, actualiza costos y precio (RF-PRE-001) y deja
 * el evento COMPRA_CONFIRMADA para sistemas externos.
 */
@Service
public class CompraService {

    public record LineaCompra(@NotNull Integer referenciaId, String unidad, @NotNull @Positive BigDecimal cantidad,
                              @NotNull @PositiveOrZero BigDecimal costoUnitario, String lote,
                              LocalDate fechaVencimiento, LocalDate fechaFabricacion) {}

    public record NuevaCompra(UUID uuid, @NotNull Long proveedorId, @NotBlank String numeroFacturaProveedor,
                              @NotNull LocalDate fechaFacturaProveedor, OffsetDateTime fechaEfectiva, String observaciones,
                              @NotEmpty List<@Valid LineaCompra> lineas) {}

    public record Resultado(long id, String documento, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}

    private final JdbcClient jdbc;
    private final Kardex kardex;
    private final Consecutivos consecutivos;
    private final InfoReferencias referencias;
    private final BandejaSalida bandeja;

    public CompraService(JdbcClient jdbc, Kardex kardex, Consecutivos consecutivos, InfoReferencias referencias, BandejaSalida bandeja) {
        this.jdbc = jdbc;
        this.kardex = kardex;
        this.consecutivos = consecutivos;
        this.referencias = referencias;
        this.bandeja = bandeja;
    }

    @Transactional
    public Resultado registrar(NuevaCompra c, Short dispositivoId) {
        UUID uuid = c.uuid() == null ? UUID.randomUUID() : c.uuid();
        Optional<Resultado> previa = porUuid(uuid);
        if (previa.isPresent()) return previa.get();   // reintento del mismo cliente: idempotente

        Boolean esProveedor = jdbc.sql("SELECT es_proveedor AND activo FROM tercero WHERE id = :id").param("id", c.proveedorId())
                .query(Boolean.class).optional().orElseThrow(() -> new NoEncontradoException("Proveedor", c.proveedorId()));
        if (!esProveedor) throw new NegocioException("El tercero " + c.proveedorId() + " no es un proveedor activo");

        OffsetDateTime fecha = c.fechaEfectiva() == null ? Fechas.ahora() : c.fechaEfectiva();
        Consecutivos.Numero numero = consecutivos.siguiente("INGRESO");
        long compraId = jdbc.sql("""
                        INSERT INTO compra (uuid, prefijo, numero_ingreso, proveedor_id, numero_factura_proveedor, fecha_factura_proveedor,
                                            fecha_efectiva, estado, observaciones, usuario_id, dispositivo_id, origen, confirmado_en)
                        VALUES (:uuid, :prefijo, :numero, :prov, :fact, :ffact, :fecha, 'CONFIRMADA', :obs, :usuario, :disp,
                                CAST(:origen AS origen_operacion), now())
                        RETURNING id""")
                .param("uuid", uuid).param("prefijo", numero.prefijo()).param("numero", numero.numero())
                .param("prov", c.proveedorId()).param("fact", c.numeroFacturaProveedor().trim())
                .param("ffact", c.fechaFacturaProveedor()).param("fecha", fecha).param("obs", c.observaciones())
                .param("usuario", UsuarioActual.id()).param("disp", dispositivoId)
                .param("origen", co.ferreteria.inventario.comun.ContextoAuditoria.origenActual())
                .query(Long.class).single();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        Set<Integer> referenciasCompradas = new LinkedHashSet<>();
        short linea = 0;
        for (LineaCompra l : c.lineas()) {
            linea++;
            InfoReferencias.Info info = referencias.info(l.referenciaId());
            if (!info.activo()) throw new NegocioException("La referencia " + info.nombre() + " está inactiva");
            InfoReferencias.Unidad unidad = referencias.unidad(l.referenciaId(), l.unidad(), false);
            Long loteId = info.controlaVencimiento()
                    ? referencias.loteParaCompra(l.referenciaId(), l.lote(), l.fechaVencimiento(), l.fechaFabricacion())
                    : null;
            long detalleId = jdbc.sql("""
                            INSERT INTO compra_detalle (compra_id, linea, referencia_id, unidad_id, cantidad, factor_a_base,
                                                        costo_unitario, porcentaje_impuesto, lote_id)
                            VALUES (:compra, :linea, :ref, :unidad, :cant, :factor, :costo, :iva, :lote) RETURNING id""")
                    .param("compra", compraId).param("linea", linea).param("ref", l.referenciaId())
                    .param("unidad", unidad.unidadId()).param("cant", l.cantidad()).param("factor", unidad.factor())
                    .param("costo", l.costoUnitario()).param("iva", info.porcentajeIva()).param("lote", loteId)
                    .query(Long.class).single();
            BigDecimal cantidadBase = Cantidades.aBase(l.cantidad(), unidad.factor());
            BigDecimal costoBase = l.costoUnitario().divide(unidad.factor(), 4, RoundingMode.HALF_UP);
            kardex.registrar(Movimiento.entradaCompra(fecha, l.referenciaId(), loteId, cantidadBase, costoBase, compraId, detalleId),
                    dispositivoId);
            BigDecimal totalLinea = l.cantidad().multiply(l.costoUnitario()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(totalLinea);
            impuestos = impuestos.add(totalLinea.multiply(info.porcentajeIva()).movePointLeft(2));
            referenciasCompradas.add(l.referenciaId());
        }
        impuestos = impuestos.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(impuestos);
        jdbc.sql("UPDATE compra SET subtotal = :s, valor_impuestos = :i, total = :t WHERE id = :id")
                .param("s", subtotal).param("i", impuestos).param("t", total).param("id", compraId).update();
        // RF-PRE-001: nuevo último costo -> precio = costo × (1 + %) redondeado; se versiona si cambia.
        referenciasCompradas.forEach(r -> jdbc.sql("SELECT fn_actualizar_precio(:r, 'COMPRA', :u)")
                .param("r", r).param("u", UsuarioActual.id()).query().singleRow());

        String documento = numero.prefijo() + "-" + numero.numero();
        bandeja.registrar("COMPRA_CONFIRMADA", "COMPRA", compraId, Map.of("documento", documento, "uuid", uuid,
                "proveedorId", c.proveedorId(), "total", total));
        return new Resultado(compraId, documento, subtotal, impuestos, total);
    }

    /** Anula un documento de ingreso con un movimiento de reverso por línea (falla si ya no hay saldo). */
    @Transactional
    public void anular(long compraId, String motivo, Short dispositivoId) {
        String estado = jdbc.sql("SELECT estado FROM compra WHERE id = :id FOR UPDATE").param("id", compraId)
                .query(String.class).optional().orElseThrow(() -> new NoEncontradoException("Compra", compraId));
        if (!"CONFIRMADA".equals(estado)) throw new NegocioException("Solo se anulan compras confirmadas");
        OffsetDateTime ahora = Fechas.ahora();
        jdbc.sql("""
                        SELECT id, referencia_id, lote_id, cantidad_base, costo_unitario_base, documento_detalle_id
                          FROM movimiento WHERE documento_tipo = 'COMPRA' AND documento_id = :id AND tipo = 'ENTRADA_COMPRA'""")
                .param("id", compraId)
                .query((rs, n) -> new Movimiento(ahora, "ANULACION_COMPRA", rs.getInt(2), (Long) rs.getObject(3), "DISPONIBLE", null,
                        rs.getBigDecimal(4), rs.getBigDecimal(5), "COMPRA", compraId, rs.getLong(6), rs.getLong(1), null))
                .list()
                .forEach(m -> kardex.registrar(m, dispositivoId));
        jdbc.sql("UPDATE compra SET estado = 'ANULADA', observaciones = concat_ws(' | ', observaciones, 'Anulada: ' || :m) WHERE id = :id")
                .param("m", motivo).param("id", compraId).update();
        bandeja.registrar("COMPRA_ANULADA", "COMPRA", compraId, Map.of("motivo", motivo));
    }

    private Optional<Resultado> porUuid(UUID uuid) {
        return jdbc.sql("SELECT id, prefijo || '-' || numero_ingreso, subtotal, valor_impuestos, total FROM compra WHERE uuid = :u")
                .param("u", uuid)
                .query((rs, n) -> new Resultado(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5)))
                .optional();
    }
}
