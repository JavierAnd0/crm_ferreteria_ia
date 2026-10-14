package co.ferreteria.inventario.auditoria;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** RF-AUD-001: consulta de la auditoría (solo lectura; la tabla rechaza UPDATE y DELETE). */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
public class AuditoriaController {

    private final JdbcClient jdbc;

    public AuditoriaController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> consultar(@RequestParam(required = false) LocalDate desde,
                                               @RequestParam(required = false) LocalDate hasta,
                                               @RequestParam(defaultValue = "") String tabla,
                                               @RequestParam(defaultValue = "") String usuario,
                                               @RequestParam(defaultValue = "") String accion,
                                               @RequestParam(defaultValue = "") String registro,
                                               @RequestParam(defaultValue = "200") int limite) {
        return jdbc.sql("""
                        SELECT a.id, a.ocurrido_en, u.login AS usuario, a.dispositivo_id, a.origen, a.tabla, a.registro_id,
                               a.accion, a.antes, a.despues
                          FROM auditoria a LEFT JOIN usuario u ON u.id = a.usuario_id
                         WHERE a.ocurrido_en >= coalesce(CAST(:desde AS date), current_date - 7)
                           AND a.ocurrido_en < coalesce(CAST(:hasta AS date), current_date) + 1
                           AND (:tabla = '' OR a.tabla = :tabla) AND (:usuario = '' OR u.login = :usuario)
                           AND (:accion = '' OR a.accion = :accion) AND (:registro = '' OR a.registro_id = :registro)
                         ORDER BY a.ocurrido_en DESC, a.id DESC LIMIT :lim""")
                .param("desde", desde).param("hasta", hasta).param("tabla", tabla).param("usuario", usuario)
                .param("accion", accion).param("registro", registro).param("lim", Math.min(limite, 2000))
                .query().listOfRows();
    }
}
