package co.ferreteria.inventario.sincronizacion;

import co.ferreteria.inventario.catalogo.CatalogoConsultas;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.inventario.Consecutivos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoints que usa la interfaz web para trabajar sin internet (RF-OFF-001). */
@RestController
@RequestMapping("/api/sync")
public class SincronizacionController {

    public record Resolucion(@NotBlank String resolucion) {}

    private final SincronizacionService servicio;
    private final Consecutivos consecutivos;
    private final CatalogoConsultas catalogo;
    private final JdbcClient jdbc;

    public SincronizacionController(SincronizacionService servicio, Consecutivos consecutivos, CatalogoConsultas catalogo, JdbcClient jdbc) {
        this.servicio = servicio;
        this.consecutivos = consecutivos;
        this.catalogo = catalogo;
        this.jdbc = jdbc;
    }

    /** Catálogo con precios y existencias para la copia local del equipo. */
    @GetMapping("/catalogo")
    public List<CatalogoConsultas.ReferenciaResumen> catalogo() {
        return catalogo.buscar("", null, false, 500);
    }

    @GetMapping("/dispositivos")
    public List<Map<String, Object>> dispositivos() {
        return jdbc.sql("SELECT id, nombre, permite_fuera_linea FROM dispositivo WHERE activo ORDER BY nombre").query().listOfRows();
    }

    /** Entrega al equipo un bloque de números de factura para usar sin conexión. */
    @PostMapping("/dispositivos/{id}/bloque")
    @PreAuthorize("hasAuthority('VENTA_EMITIR')")
    @Transactional
    public Consecutivos.Rango bloque(@PathVariable short id, @RequestParam(defaultValue = "50") @Min(1) @Max(500) int cantidad) {
        Boolean permite = jdbc.sql("SELECT permite_fuera_linea AND activo FROM dispositivo WHERE id = :id").param("id", id)
                .query(Boolean.class).optional().orElse(false);
        if (!permite) throw new NegocioException("El equipo " + id + " no está autorizado para operar sin internet");
        jdbc.sql("UPDATE dispositivo SET ultima_sincronizacion = now() WHERE id = :id").param("id", id).update();
        return consecutivos.reservarBloque(id, cantidad);
    }

    @PostMapping("/ventas")
    @PreAuthorize("hasAuthority('VENTA_EMITIR')")
    public List<SincronizacionService.ResultadoSync> ventas(@RequestBody List<SincronizacionService.@Valid OperacionVenta> operaciones) {
        List<SincronizacionService.ResultadoSync> resultados = new ArrayList<>();
        for (var op : operaciones) {
            var previa = servicio.yaProcesada(op.uuid());
            if (previa.isPresent()) {
                resultados.add(previa.get());
                continue;
            }
            try {
                resultados.add(servicio.aplicar(op));
            } catch (RuntimeException e) {
                resultados.add(servicio.registrarConflicto(op, SincronizacionService.motivo(e)));
            }
        }
        return resultados;
    }

    @GetMapping("/conflictos")
    @PreAuthorize("hasAnyAuthority('PRODUCTO_EDITAR','VENTA_ANULAR')")
    public List<Map<String, Object>> conflictos() {
        return jdbc.sql("""
                        SELECT o.uuid, d.nombre AS dispositivo, u.login AS usuario, o.creado_local_en, o.recibido_en,
                               o.detalle_conflicto, o.carga
                          FROM operacion_fuera_linea o JOIN dispositivo d ON d.id = o.dispositivo_id JOIN usuario u ON u.id = o.usuario_id
                         WHERE o.estado = 'CONFLICTO' ORDER BY o.recibido_en""").query().listOfRows();
    }

    @PostMapping("/conflictos/{uuid}/resolver")
    @PreAuthorize("hasAnyAuthority('PRODUCTO_EDITAR','VENTA_ANULAR')")
    public void resolver(@PathVariable UUID uuid, @Valid @RequestBody Resolucion r) {
        servicio.resolverConflicto(uuid, r.resolucion());
    }
}
