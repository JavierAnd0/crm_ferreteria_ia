package co.ferreteria.inventario.inventario;

import co.ferreteria.inventario.comun.Fechas;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RF-EST-001: reservas (DISPONIBLE ↔ RESERVADA) y existencias dañadas (DISPONIBLE ↔ DANADA).
 * Un cambio de estado no altera el total; la base de datos impide saldos negativos.
 */
@Service
public class EstadoService {

    public record NuevaReserva(UUID uuid, @NotNull Long clienteId, @NotNull Integer referenciaId,
                               @NotNull @Positive BigDecimal cantidadBase, Long loteId) {}

    public record NuevoCambio(@NotNull Integer referenciaId, Long loteId, @NotNull @Positive BigDecimal cantidadBase,
                              @NotBlank @Pattern(regexp = "DANAR|RECUPERAR") String accion, @NotBlank String motivo) {}

    private final JdbcClient jdbc;
    private final Kardex kardex;
    private final InfoReferencias referencias;
    private final int horasVigencia;

    public EstadoService(JdbcClient jdbc, Kardex kardex, InfoReferencias referencias,
                         @Value("${app.reservas.horas-vigencia:72}") int horasVigencia) {
        this.jdbc = jdbc;
        this.kardex = kardex;
        this.referencias = referencias;
        this.horasVigencia = horasVigencia;
    }

    @Transactional
    public long reservar(NuevaReserva r, Short dispositivoId) {
        UUID uuid = r.uuid() == null ? UUID.randomUUID() : r.uuid();
        var previa = jdbc.sql("SELECT id FROM reserva WHERE uuid = :u").param("u", uuid).query(Long.class).optional();
        if (previa.isPresent()) return previa.get();
        Boolean esCliente = jdbc.sql("SELECT es_cliente FROM tercero WHERE id = :id").param("id", r.clienteId())
                .query(Boolean.class).optional().orElseThrow(() -> new NoEncontradoException("Cliente", r.clienteId()));
        if (!esCliente) throw new NegocioException("Solo se reserva para clientes");
        InfoReferencias.Info info = referencias.info(r.referenciaId());
        Long lote = r.loteId();
        if (info.controlaVencimiento() && lote == null)
            lote = referencias.loteFefo(r.referenciaId(), "DISPONIBLE", r.cantidadBase(), Fechas.hoy());
        OffsetDateTime ahora = Fechas.ahora();
        long id = jdbc.sql("""
                        INSERT INTO reserva (uuid, cliente_id, referencia_id, lote_id, cantidad_base, vence_en, usuario_id)
                        VALUES (:u, :c, :r, :l, :q, :vence, :usuario) RETURNING id""")
                .param("u", uuid).param("c", r.clienteId()).param("r", r.referenciaId()).param("l", lote)
                .param("q", r.cantidadBase()).param("vence", ahora.plusHours(horasVigencia)).param("usuario", UsuarioActual.id())
                .query(Long.class).single();
        kardex.registrar(Movimiento.cambioEstado(ahora, r.referenciaId(), lote, "DISPONIBLE", "RESERVADA", r.cantidadBase(), "RESERVA", id),
                dispositivoId);
        return id;
    }

    @Transactional
    public void liberar(long reservaId, String nuevoEstado, Short dispositivoId) {
        record Reserva(int referenciaId, Long loteId, BigDecimal cantidadBase, String estado) {}
        Reserva r = jdbc.sql("SELECT referencia_id, lote_id, cantidad_base, estado FROM reserva WHERE id = :id FOR UPDATE")
                .param("id", reservaId).query(Reserva.class).optional()
                .orElseThrow(() -> new NoEncontradoException("Reserva", reservaId));
        if (!"ACTIVA".equals(r.estado())) throw new NegocioException("La reserva no está activa");
        kardex.registrar(Movimiento.cambioEstado(Fechas.ahora(), r.referenciaId(), r.loteId(), "RESERVADA", "DISPONIBLE",
                r.cantidadBase(), "RESERVA", reservaId), dispositivoId);
        jdbc.sql("UPDATE reserva SET estado = :e WHERE id = :id").param("e", nuevoEstado).param("id", reservaId).update();
    }

    /** Ids de reservas activas vencidas; el programador las libera una por una. */
    @Transactional(readOnly = true)
    public List<Long> reservasVencidas() {
        return jdbc.sql("SELECT id FROM reserva WHERE estado = 'ACTIVA' AND vence_en < now() ORDER BY id LIMIT 500")
                .query(Long.class).list();
    }

    @Transactional
    public long cambiarEstado(NuevoCambio c, Short dispositivoId) {
        boolean danar = "DANAR".equals(c.accion());
        String origen = danar ? "DISPONIBLE" : "DANADA";
        String destino = danar ? "DANADA" : "DISPONIBLE";
        InfoReferencias.Info info = referencias.info(c.referenciaId());
        if (info.controlaVencimiento() && c.loteId() == null) throw new NegocioException("Indique el lote");
        long id = jdbc.sql("""
                        INSERT INTO cambio_estado (referencia_id, lote_id, estado_origen, estado_destino, cantidad_base, motivo, usuario_id)
                        VALUES (:r, :l, CAST(:o AS estado_inventario), CAST(:d AS estado_inventario), :q, :m, :u) RETURNING id""")
                .param("r", c.referenciaId()).param("l", c.loteId()).param("o", origen).param("d", destino)
                .param("q", c.cantidadBase()).param("m", c.motivo()).param("u", UsuarioActual.id())
                .query(Long.class).single();
        kardex.registrar(Movimiento.cambioEstado(Fechas.ahora(), c.referenciaId(), c.loteId(), origen, destino, c.cantidadBase(),
                "CAMBIO_ESTADO", id), dispositivoId);
        return id;
    }
}
