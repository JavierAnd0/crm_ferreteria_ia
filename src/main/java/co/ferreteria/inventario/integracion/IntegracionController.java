package co.ferreteria.inventario.integracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Configuración de sistemas externos y consulta / reintento de eventos. */
@RestController
@RequestMapping("/api/integracion")
@PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
public class IntegracionController {

    public record SistemaExterno(@NotBlank String codigo, @NotBlank String nombre,
                                 @Pattern(regexp = "FACTURACION_ELECTRONICA|CONTABILIDAD|COMERCIO_ELECTRONICO|OTRO") String tipo,
                                 @Pattern(regexp = "https?://.+") String endpointUrl, boolean activo) {}

    private final JdbcClient jdbc;

    public IntegracionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/sistemas")
    public List<Map<String, Object>> sistemas() {
        return jdbc.sql("SELECT id, codigo, nombre, tipo, endpoint_url, activo FROM sistema_externo ORDER BY codigo").query().listOfRows();
    }

    @PostMapping("/sistemas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> guardar(@Valid @RequestBody SistemaExterno s) {
        return jdbc.sql("""
                        INSERT INTO sistema_externo (codigo, nombre, tipo, endpoint_url, activo) VALUES (:c, :n, :t, :u, :a)
                        ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre, tipo = EXCLUDED.tipo,
                               endpoint_url = EXCLUDED.endpoint_url, activo = EXCLUDED.activo
                        RETURNING id, codigo, nombre, tipo, endpoint_url, activo""")
                .param("c", s.codigo()).param("n", s.nombre()).param("t", s.tipo() == null ? "OTRO" : s.tipo())
                .param("u", s.endpointUrl()).param("a", s.activo())
                .query().singleRow();
    }

    @GetMapping("/eventos")
    public List<Map<String, Object>> eventos(@RequestParam(defaultValue = "") String estado) {
        return jdbc.sql("""
                        SELECT e.id, s.codigo AS sistema, e.tipo_evento, e.entidad, e.entidad_id, e.estado, e.intentos,
                               e.proximo_intento, e.ultimo_error, e.creado_en
                          FROM evento_integracion e JOIN sistema_externo s ON s.id = e.sistema_id
                         WHERE :estado = '' OR e.estado = :estado ORDER BY e.id DESC LIMIT 200""")
                .param("estado", estado).query().listOfRows();
    }

    @PostMapping("/eventos/{id}/reintentar")
    @Transactional
    public void reintentar(@PathVariable long id) {
        jdbc.sql("UPDATE evento_integracion SET estado = 'PENDIENTE', proximo_intento = now() WHERE id = :id AND estado = 'ERROR'")
                .param("id", id).update();
    }
}
