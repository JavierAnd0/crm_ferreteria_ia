package co.ferreteria.inventario.precios;

import co.ferreteria.inventario.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/referencias/{id}/precio")
public class PrecioController {

    public record CambioPorcentaje(@NotNull BigDecimal porcentaje) {}
    public record PrecioManual(@NotNull BigDecimal precio, @NotBlank String motivo) {}

    private final PrecioService servicio;
    private final JdbcClient jdbc;

    public PrecioController(PrecioService servicio, JdbcClient jdbc) {
        this.servicio = servicio;
        this.jdbc = jdbc;
    }

    @PutMapping("/porcentaje")
    @PreAuthorize("hasAuthority('PRECIO_CAMBIAR')")
    public Map<String, BigDecimal> cambiarPorcentaje(@PathVariable int id, @Valid @RequestBody CambioPorcentaje c) {
        return Map.of("precioVenta", servicio.cambiarPorcentaje(id, c.porcentaje()));
    }

    @PutMapping("/manual")
    @PreAuthorize("hasAuthority('PRECIO_CAMBIAR')")
    public void manual(@PathVariable int id, @Valid @RequestBody PrecioManual p) {
        servicio.fijarPrecioManual(id, p.precio(), p.motivo());
    }

    @GetMapping("/historial")
    public List<Map<String, Object>> historial(@PathVariable int id) {
        boolean verCosto = UsuarioActual.tienePermiso("COSTO_VER");
        return jdbc.sql("""
                        SELECT h.id, CASE WHEN :costo THEN h.costo_base END AS costo_base, h.porcentaje_incremento,
                               h.precio_calculado, h.precio_final, h.motivo, h.vigente_desde, h.vigente_hasta, u.login AS usuario
                          FROM precio_historial h JOIN usuario u ON u.id = h.usuario_id
                         WHERE h.referencia_id = :id ORDER BY h.vigente_desde DESC""")
                .param("costo", verCosto).param("id", id).query().listOfRows();
    }
}
