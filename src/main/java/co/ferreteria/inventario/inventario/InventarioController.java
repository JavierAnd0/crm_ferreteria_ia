package co.ferreteria.inventario.inventario;

import co.ferreteria.inventario.comun.ContextoAuditoria;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InventarioController {

    private final EstadoService estados;
    private final JdbcClient jdbc;

    public InventarioController(EstadoService estados, JdbcClient jdbc) {
        this.estados = estados;
        this.jdbc = jdbc;
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVA_GESTIONAR')")
    public Map<String, Long> reservar(@Valid @RequestBody EstadoService.NuevaReserva r,
                                      @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short disp) {
        return Map.of("id", estados.reservar(r, disp));
    }

    @PostMapping("/reservas/{id}/liberar")
    @PreAuthorize("hasAuthority('RESERVA_GESTIONAR')")
    public void liberar(@PathVariable long id,
                        @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short disp) {
        estados.liberar(id, "LIBERADA", disp);
    }

    @GetMapping("/reservas")
    public List<Map<String, Object>> reservas(@RequestParam(defaultValue = "ACTIVA") String estado) {
        return jdbc.sql("""
                        SELECT r.id, r.estado, r.cantidad_base, r.vence_en, r.creado_en, r.referencia_id, r.cliente_id,
                               p.nombre || ' ' || m.nombre || ' ' || rf.presentacion AS referencia,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS cliente, l.codigo_lote
                          FROM reserva r JOIN referencia rf ON rf.id = r.referencia_id JOIN producto p ON p.id = rf.producto_id
                          JOIN marca m ON m.id = rf.marca_id JOIN tercero t ON t.id = r.cliente_id
                          LEFT JOIN lote l ON l.id = r.lote_id
                         WHERE r.estado = :e ORDER BY r.vence_en LIMIT 500""")
                .param("e", estado).query().listOfRows();
    }

    @PostMapping("/existencias/cambios-estado")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ESTADO_DANADO')")
    public Map<String, Long> cambiarEstado(@Valid @RequestBody EstadoService.NuevoCambio c,
                                           @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short disp) {
        return Map.of("id", estados.cambiarEstado(c, disp));
    }

    /** RF-MOV-003: kárdex de una referencia con saldo y costo promedio después de cada movimiento. */
    @GetMapping("/kardex")
    @PreAuthorize("hasAnyAuthority('PRODUCTO_EDITAR','REPORTE_CONSULTAR')")
    public List<Map<String, Object>> kardex(@RequestParam(required = false) Integer referenciaId,
                                            @RequestParam(required = false) LocalDate desde,
                                            @RequestParam(required = false) LocalDate hasta,
                                            @RequestParam(defaultValue = "500") int limite) {
        return jdbc.sql("""
                        SELECT m.id, m.fecha_efectiva, m.tipo, m.referencia_id, r.codigo_barras,
                               p.nombre || ' ' || ma.nombre || ' ' || r.presentacion AS referencia, l.codigo_lote,
                               m.estado_origen, m.estado_destino, m.cantidad_base, m.costo_unitario_base,
                               m.saldo_total_despues, m.costo_promedio_despues, m.documento_tipo, m.documento_id,
                               CASE m.documento_tipo
                                 WHEN 'COMPRA' THEN (SELECT prefijo || '-' || numero_ingreso FROM compra WHERE id = m.documento_id)
                                 WHEN 'VENTA'  THEN (SELECT prefijo || '-' || numero_factura FROM venta WHERE id = m.documento_id)
                                 ELSE m.documento_tipo || '-' || m.documento_id END AS documento,
                               u.login AS usuario, m.origen, m.reversa_movimiento_id
                          FROM movimiento m JOIN referencia r ON r.id = m.referencia_id JOIN producto p ON p.id = r.producto_id
                          JOIN marca ma ON ma.id = r.marca_id JOIN usuario u ON u.id = m.usuario_id
                          LEFT JOIN lote l ON l.id = m.lote_id
                         WHERE (CAST(:ref AS integer) IS NULL OR m.referencia_id = CAST(:ref AS integer))
                           AND m.fecha_efectiva >= coalesce(CAST(:desde AS date), current_date - 30)
                           AND m.fecha_efectiva < coalesce(CAST(:hasta AS date), current_date) + 1
                         ORDER BY m.fecha_efectiva DESC, m.id DESC LIMIT :lim""")
                .param("ref", referenciaId).param("desde", desde).param("hasta", hasta).param("lim", Math.min(limite, 5000))
                .query().listOfRows();
    }
}
