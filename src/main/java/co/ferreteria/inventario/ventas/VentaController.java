package co.ferreteria.inventario.ventas;

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
@RequestMapping("/api/ventas")
public class VentaController {

    public record Anulacion(@NotBlank String motivo) {}

    private final VentaService servicio;
    private final JdbcClient jdbc;

    public VentaController(VentaService servicio, JdbcClient jdbc) {
        this.servicio = servicio;
        this.jdbc = jdbc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VENTA_EMITIR')")
    public VentaService.Resultado emitir(@Valid @RequestBody VentaService.NuevaVenta venta,
                                         @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short dispositivo) {
        return servicio.emitir(venta, dispositivo);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('VENTA_ANULAR')")
    public void anular(@PathVariable long id, @Valid @RequestBody Anulacion a,
                       @RequestHeader(value = ContextoAuditoria.CABECERA_DISPOSITIVO, required = false) Short dispositivo) {
        servicio.anular(id, a.motivo(), dispositivo);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VENTA_EMITIR','REPORTE_CONSULTAR')")
    public List<Map<String, Object>> listar(@RequestParam(required = false) LocalDate desde,
                                            @RequestParam(required = false) LocalDate hasta) {
        return jdbc.sql("""
                        SELECT v.id, v.prefijo || '-' || v.numero_factura AS factura, v.fecha_emision, v.estado, v.forma_pago,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS cliente, v.origen,
                               v.subtotal, v.valor_descuento, v.valor_impuestos, v.total
                          FROM venta v JOIN tercero t ON t.id = v.cliente_id
                         WHERE v.fecha_emision >= coalesce(CAST(:desde AS date), current_date - 7)
                           AND v.fecha_emision < coalesce(CAST(:hasta AS date), current_date) + 1
                         ORDER BY v.fecha_emision DESC LIMIT 500""")
                .param("desde", desde).param("hasta", hasta).query().listOfRows();
    }

    /** Factura completa para mostrar o imprimir. */
    @GetMapping("/{id}")
    public Map<String, Object> factura(@PathVariable long id) {
        Map<String, Object> cabecera = jdbc.sql("""
                        SELECT v.id, v.prefijo || '-' || v.numero_factura AS factura, v.fecha_emision, v.estado, v.forma_pago,
                               v.subtotal, v.valor_descuento, v.valor_impuestos, v.total, v.cufe, v.estado_envio_fe,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS cliente,
                               t.tipo_identificacion || ' ' || t.numero_identificacion ||
                                 coalesce('-' || t.digito_verificacion, '') AS identificacion_cliente,
                               t.direccion, t.telefono, t.correo, u.nombre_completo AS vendedor
                          FROM venta v JOIN tercero t ON t.id = v.cliente_id JOIN usuario u ON u.id = v.usuario_id
                         WHERE v.id = :id""")
                .param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new NoEncontradoException("Venta", id));
        List<Map<String, Object>> lineas = jdbc.sql("""
                        SELECT d.linea, r.codigo_barras, p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS referencia,
                               u.codigo AS unidad, d.cantidad, d.precio_unitario, d.valor_descuento, d.porcentaje_impuesto,
                               d.total_linea, l.codigo_lote, d.fecha_fin_garantia
                          FROM venta_detalle d JOIN referencia r ON r.id = d.referencia_id JOIN producto p ON p.id = r.producto_id
                          JOIN marca m ON m.id = r.marca_id JOIN unidad_medida u ON u.id = d.unidad_id
                          LEFT JOIN lote l ON l.id = d.lote_id
                         WHERE d.venta_id = :id ORDER BY d.linea""")
                .param("id", id).query().listOfRows();
        return Map.of("factura", cabecera, "lineas", lineas);
    }
}
