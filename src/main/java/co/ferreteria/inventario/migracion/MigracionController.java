package co.ferreteria.inventario.migracion;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/migracion")
@PreAuthorize("hasAuthority('MIGRACION_EJECUTAR')")
public class MigracionController {

    private final MigracionService servicio;
    private final JdbcClient jdbc;

    public MigracionController(MigracionService servicio, JdbcClient jdbc) {
        this.servicio = servicio;
        this.jdbc = jdbc;
    }

    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> plantilla() throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plantilla_migracion.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(HojasExcel.plantilla());
    }

    /** Paso 1: sube el Excel, guarda y valida cada fila. No modifica inventario. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MigracionService.Resumen recibir(@RequestParam("archivo") MultipartFile archivo,
                                            @RequestParam("fechaCorte") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte)
            throws Exception {
        return servicio.recibir(archivo.getOriginalFilename(), archivo.getBytes(), fechaCorte);
    }

    /** Paso 2: carga las filas válidas. Las que fallan quedan en ERROR con su motivo. */
    @PostMapping("/{id}/cargar")
    public MigracionService.Resumen cargar(@PathVariable long id) throws Exception {
        for (Long fila : servicio.filasPorCargar(id)) {
            try {
                servicio.cargarFila(fila);
            } catch (Exception e) {
                servicio.marcarError(fila, e.getMessage());
            }
        }
        return servicio.finalizar(id);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable long id) {
        List<Map<String, Object>> filas = jdbc.sql("""
                        SELECT hoja, numero_fila, estado, errores, datos FROM importacion_fila
                         WHERE importacion_id = :i ORDER BY hoja, numero_fila""").param("i", id).query().listOfRows();
        return Map.of("resumen", servicio.resumen(id), "filas", filas);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.sql("""
                        SELECT i.id, i.archivo_nombre, i.fecha_corte, i.estado, i.filas_total, i.filas_validas, i.filas_error,
                               i.creado_en, u.login AS usuario
                          FROM importacion i JOIN usuario u ON u.id = i.usuario_id ORDER BY i.id DESC""").query().listOfRows();
    }
}
