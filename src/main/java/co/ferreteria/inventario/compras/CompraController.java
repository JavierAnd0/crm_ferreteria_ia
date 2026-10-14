package co.ferreteria.inventario.compras;

import co.ferreteria.inventario.comun.ContextoAuditoria;
import co.ferreteria.inventario.comun.NoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    public record Anulacion(@NotBlank String motivo) {}

    private final CompraService servicio;
    private final JdbcClient jdbc;

    public CompraController(CompraService servicio, JdbcClient jdbc) {
        this.servicio = servicio;
        this.jdbc = jdbc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPRA_REGISTRAR')")
    public CompraService.Resultado registrar(@Valid @RequestBody CompraService.NuevaCompra compra,
                                             @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short dispositivo) {
        return servicio.registrar(compra, dispositivo);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('COMPRA_ANULAR')")
    public void anular(@PathVariable long id, @Valid @RequestBody Anulacion a,
                       @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short dispositivo) {
        servicio.anular(id, a.motivo(), dispositivo);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPRA_REGISTRAR','COSTO_VER')")
    public List<Map<String, Object>> listar(@RequestParam(required = false) LocalDate desde,
                                            @RequestParam(required = false) LocalDate hasta) {
        return jdbc.sql("""
                        SELECT c.id, c.prefijo || '-' || c.numero_ingreso AS documento, c.fecha_efectiva, c.estado,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS proveedor,
                               c.numero_factura_proveedor, c.subtotal, c.valor_impuestos, c.total
                          FROM compra c JOIN tercero t ON t.id = c.proveedor_id
                         WHERE c.fecha_efectiva >= coalesce(CAST(:desde AS date), current_date - 30)
                           AND c.fecha_efectiva < coalesce(CAST(:hasta AS date), current_date) + 1
                         ORDER BY c.fecha_efectiva DESC LIMIT 500""")
                .param("desde", desde).param("hasta", hasta).query().listOfRows();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('COMPRA_REGISTRAR','COSTO_VER')")
    public Map<String, Object> detalle(@PathVariable long id) {
        Map<String, Object> cabecera = jdbc.sql("""
                        SELECT c.*, c.prefijo || '-' || c.numero_ingreso AS documento,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS proveedor,
                               t.tipo_identificacion || ' ' || t.numero_identificacion AS identificacion_proveedor
                          FROM compra c JOIN tercero t ON t.id = c.proveedor_id WHERE c.id = :id""")
                .param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new NoEncontradoException("Compra", id));
        List<Map<String, Object>> lineas = jdbc.sql("""
                        SELECT d.linea, r.codigo_barras, p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS referencia,
                               u.codigo AS unidad, d.cantidad, d.cantidad_base, d.costo_unitario, d.porcentaje_impuesto,
                               d.total_linea, l.codigo_lote, l.fecha_vencimiento
                          FROM compra_detalle d JOIN referencia r ON r.id = d.referencia_id JOIN producto p ON p.id = r.producto_id
                          JOIN marca m ON m.id = r.marca_id JOIN unidad_medida u ON u.id = d.unidad_id
                          LEFT JOIN lote l ON l.id = d.lote_id
                         WHERE d.compra_id = :id ORDER BY d.linea""")
                .param("id", id).query().listOfRows();
        return Map.of("compra", cabecera, "lineas", lineas);
    }
}
