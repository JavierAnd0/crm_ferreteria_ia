package co.ferreteria.inventario.sincronizacion;

import co.ferreteria.inventario.comun.ContextoAuditoria;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import co.ferreteria.inventario.ventas.VentaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * RF-OFF-001. Cada venta hecha sin internet llega con el UUID generado en el equipo. Si ya se
 * procesó, se devuelve el resultado anterior (idempotencia). Si la venta incumple una regla al
 * aplicarla en el servidor (por ejemplo, otra caja vendió la última unidad), queda en CONFLICTO
 * para que un responsable la resuelva; nunca se genera un saldo negativo.
 */
@Service
public class SincronizacionService {

    public record OperacionVenta(@NotNull UUID uuid, @NotNull Short dispositivoId, @NotBlank String prefijo,
                                 @NotNull @Positive Long numero, @NotNull OffsetDateTime creadoLocalEn,
                                 @NotNull @Valid VentaService.NuevaVenta venta) {}

    public record ResultadoSync(UUID uuid, String estado, String factura, String detalle) {}

    private final JdbcClient jdbc;
    private final VentaService ventas;
    private final ObjectMapper json;

    public SincronizacionService(JdbcClient jdbc, VentaService ventas, ObjectMapper json) {
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.json = json;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ResultadoSync> yaProcesada(UUID uuid) {
        return jdbc.sql("SELECT uuid, estado, detalle_conflicto FROM operacion_fuera_linea WHERE uuid = :u").param("u", uuid)
                .query((rs, n) -> new ResultadoSync(uuid, rs.getString(2), facturaDe(uuid), rs.getString(3)))
                .optional();
    }

    /** Aplica la venta y registra la operación como APLICADA en la misma transacción. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoSync aplicar(OperacionVenta op) {
        VentaService.NuevaVenta venta = new VentaService.NuevaVenta(op.uuid(), op.venta().clienteId(), op.venta().formaPago(),
                op.venta().lineas());
        VentaService.Resultado r = ContextoAuditoria.conOrigen("FUERA_DE_LINEA",
                () -> ventas.emitirFueraDeLinea(venta, op.dispositivoId(), op.prefijo(), op.numero(), op.creadoLocalEn()));
        guardar(op, "APLICADA", null);
        return new ResultadoSync(op.uuid(), "APLICADA", r.factura(), null);
    }

    /** Guarda la operación en conflicto en una transacción aparte (la venta fallida se revirtió). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoSync registrarConflicto(OperacionVenta op, String motivo) {
        guardar(op, "CONFLICTO", motivo);
        return new ResultadoSync(op.uuid(), "CONFLICTO", null, motivo);
    }

    @Transactional
    public void resolverConflicto(UUID uuid, String resolucion) {
        int filas = jdbc.sql("""
                        UPDATE operacion_fuera_linea SET estado = 'RECHAZADA', resuelto_por = :u, resuelto_en = now(),
                               detalle_conflicto = detalle_conflicto || ' | Resolución: ' || :r
                         WHERE uuid = :id AND estado = 'CONFLICTO'""")
                .param("u", UsuarioActual.id()).param("r", resolucion).param("id", uuid).update();
        if (filas == 0) throw new NegocioException("La operación no está en conflicto");
    }

    private void guardar(OperacionVenta op, String estado, String detalle) {
        try {
            jdbc.sql("""
                            INSERT INTO operacion_fuera_linea (uuid, dispositivo_id, usuario_id, tipo_operacion, carga, creado_local_en,
                                                               estado, detalle_conflicto)
                            VALUES (:u, :d, :usr, 'VENTA', CAST(:carga AS jsonb), :local, :e, :det)""")
                    .param("u", op.uuid()).param("d", op.dispositivoId()).param("usr", UsuarioActual.id())
                    .param("carga", json.writeValueAsString(op)).param("local", op.creadoLocalEn())
                    .param("e", estado).param("det", detalle).update();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String facturaDe(UUID uuid) {
        return jdbc.sql("SELECT prefijo || '-' || numero_factura FROM venta WHERE uuid = :u").param("u", uuid)
                .query(String.class).optional().orElse(null);
    }

    static String motivo(RuntimeException e) {
        if (e instanceof DataAccessException d && d.getMostSpecificCause() != null) {
            String m = d.getMostSpecificCause().getMessage();
            int corte = m.indexOf("\n");
            return corte > 0 ? m.substring(0, corte) : m;
        }
        return e.getMessage();
    }
}
