package co.ferreteria.inventario.reportes;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** RF-REP-001: reportes básicos de ventas, margen, valoración, garantías y stock bajo. */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAuthority('REPORTE_CONSULTAR')")
public class ReporteController {

    private final JdbcClient jdbc;

    public ReporteController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/ventas-diarias")
    public List<Map<String, Object>> ventasDiarias(@RequestParam(required = false) LocalDate desde,
                                                   @RequestParam(required = false) LocalDate hasta) {
        return jdbc.sql("""
                        SELECT (fecha_emision AT TIME ZONE 'America/Bogota')::date AS dia, count(*) AS facturas,
                               sum(subtotal - valor_descuento) AS base, sum(valor_impuestos) AS iva, sum(total) AS total
                          FROM venta WHERE estado = 'EMITIDA'
                           AND (fecha_emision AT TIME ZONE 'America/Bogota')::date
                               BETWEEN coalesce(CAST(:desde AS date), (now() AT TIME ZONE 'America/Bogota')::date - 30) AND coalesce(CAST(:hasta AS date), (now() AT TIME ZONE 'America/Bogota')::date)
                         GROUP BY 1 ORDER BY 1""")
                .param("desde", desde).param("hasta", hasta).query().listOfRows();
    }

    @GetMapping("/margen")
    @PreAuthorize("hasAuthority('COSTO_VER')")
    public List<Map<String, Object>> margen(@RequestParam(required = false) LocalDate desde,
                                            @RequestParam(required = false) LocalDate hasta) {
        return jdbc.sql("""
                        SELECT r.codigo_barras, p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS referencia,
                               sum(d.cantidad_base) AS cantidad_base, sum(d.total_linea) AS venta,
                               round(sum(d.cantidad_base * d.costo_unitario_base), 2) AS costo,
                               sum(d.total_linea) - round(sum(d.cantidad_base * d.costo_unitario_base), 2) AS margen
                          FROM venta v JOIN venta_detalle d ON d.venta_id = v.id JOIN referencia r ON r.id = d.referencia_id
                          JOIN producto p ON p.id = r.producto_id JOIN marca m ON m.id = r.marca_id
                         WHERE v.estado = 'EMITIDA'
                           AND (v.fecha_emision AT TIME ZONE 'America/Bogota')::date
                               BETWEEN coalesce(CAST(:desde AS date), (now() AT TIME ZONE 'America/Bogota')::date - 30) AND coalesce(CAST(:hasta AS date), (now() AT TIME ZONE 'America/Bogota')::date)
                         GROUP BY r.id, p.nombre, m.nombre ORDER BY margen DESC""")
                .param("desde", desde).param("hasta", hasta).query().listOfRows();
    }

    @GetMapping("/valoracion")
    @PreAuthorize("hasAuthority('COSTO_VER')")
    public List<Map<String, Object>> valoracion() {
        return jdbc.sql("""
                        SELECT referencia_id, codigo_barras, producto, marca, presentacion, unidad_base, disponible, reservada,
                               danada, total, costo_promedio, round(total * costo_promedio, 2) AS valor
                          FROM v_existencia_resumen WHERE total > 0 ORDER BY valor DESC""").query().listOfRows();
    }

    @GetMapping("/garantias-vigentes")
    public List<Map<String, Object>> garantias() {
        return jdbc.sql("""
                        SELECT v.prefijo || '-' || v.numero_factura AS factura, v.fecha_emision,
                               coalesce(t.razon_social, t.nombres || ' ' || t.apellidos) AS cliente,
                               p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS referencia, d.fecha_fin_garantia
                          FROM venta_detalle d JOIN venta v ON v.id = d.venta_id JOIN tercero t ON t.id = v.cliente_id
                          JOIN referencia r ON r.id = d.referencia_id JOIN producto p ON p.id = r.producto_id
                          JOIN marca m ON m.id = r.marca_id
                         WHERE v.estado = 'EMITIDA' AND d.fecha_fin_garantia >= current_date
                         ORDER BY d.fecha_fin_garantia""").query().listOfRows();
    }
}
