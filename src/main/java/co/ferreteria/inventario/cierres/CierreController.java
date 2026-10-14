package co.ferreteria.inventario.cierres;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periodos")
public class CierreController {

    public record Reapertura(@NotBlank String motivo) {}

    private final CierreService servicio;
    private final JdbcClient jdbc;

    public CierreController(CierreService servicio, JdbcClient jdbc) {
        this.servicio = servicio;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.sql("""
                        SELECT p.id, p.fecha_inicio, p.fecha_fin, p.estado, p.cierre_programado, p.cerrado_en,
                               uc.login AS cerrado_por, p.reabierto_en, ur.login AS reabierto_por, p.motivo_reapertura
                          FROM periodo p LEFT JOIN usuario uc ON uc.id = p.cerrado_por LEFT JOIN usuario ur ON ur.id = p.reabierto_por
                         ORDER BY p.id DESC""").query().listOfRows();
    }

    @PostMapping("/{periodo}/cerrar")
    @PreAuthorize("hasAuthority('CIERRE_EJECUTAR')")
    public Map<String, Integer> cerrar(@PathVariable int periodo) {
        return Map.of("saldos", servicio.cerrar(periodo));
    }

    @PostMapping("/{periodo}/reabrir")
    @PreAuthorize("hasAuthority('CIERRE_REABRIR')")
    public void reabrir(@PathVariable int periodo, @Valid @RequestBody Reapertura r) {
        servicio.reabrir(periodo, r.motivo());
    }

    @GetMapping("/{periodo}/saldos")
    @PreAuthorize("hasAnyAuthority('COSTO_VER','REPORTE_CONSULTAR')")
    public List<Map<String, Object>> saldos(@PathVariable int periodo) {
        return jdbc.sql("""
                        SELECT r.codigo_barras, p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS referencia,
                               l.codigo_lote, c.estado, c.cantidad, c.costo_promedio, c.valor_total
                          FROM cierre_saldo c JOIN referencia r ON r.id = c.referencia_id JOIN producto p ON p.id = r.producto_id
                          JOIN marca m ON m.id = r.marca_id LEFT JOIN lote l ON l.id = c.lote_id
                         WHERE c.periodo_id = :p ORDER BY p.nombre, m.nombre""")
                .param("p", periodo).query().listOfRows();
    }
}
