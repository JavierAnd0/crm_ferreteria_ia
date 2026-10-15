package co.ferreteria.inventario.catalogo;

import co.ferreteria.inventario.comun.NoEncontradoException;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lecturas del catálogo y existencias (RF-PRO-002). Los costos solo se muestran con COSTO_VER. */
@Component
public class CatalogoConsultas {

    public record ReferenciaResumen(Integer id, String codigoBarras, String codigoInterno, String producto, String marca,
                                    String presentacion, String unidadBase, BigDecimal disponible, BigDecimal reservada,
                                    BigDecimal danada, BigDecimal precioVenta, BigDecimal porcentajeIva,
                                    BigDecimal porcentajeIncremento, BigDecimal costoPromedio, BigDecimal stockMinimo,
                                    boolean controlaVencimiento, boolean permiteFraccion, BigDecimal fraccionMinima,
                                    Short mesesGarantia, boolean activo, Integer productoId) {}

    public record UnidadReferencia(String unidad, BigDecimal factor, boolean usoCompra, boolean usoVenta) {}

    public record LoteSaldo(Long id, String codigoLote, LocalDate fechaVencimiento, BigDecimal disponible,
                            BigDecimal reservada, BigDecimal danada) {}

    public record DetalleReferencia(ReferenciaResumen referencia, List<UnidadReferencia> unidades, List<LoteSaldo> lotes) {}

    private static final String SELECT_RESUMEN = """
            SELECT r.id, r.codigo_barras, p.codigo_interno, p.nombre AS producto, m.nombre AS marca, r.presentacion,
                   u.codigo AS unidad_base,
                   coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DISPONIBLE'), 0) AS disponible,
                   coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'RESERVADA'), 0) AS reservada,
                   coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DANADA'), 0) AS danada,
                   r.precio_venta, t.porcentaje AS porcentaje_iva, r.porcentaje_incremento, r.costo_promedio,
                   r.stock_minimo, p.controla_vencimiento, r.permite_fraccion, r.fraccion_minima, r.meses_garantia,
                   r.activo AND p.activo AS activo, p.id AS producto_id
              FROM referencia r
              JOIN producto p ON p.id = r.producto_id
              JOIN marca m ON m.id = r.marca_id
              JOIN unidad_medida u ON u.id = r.unidad_base_id
              JOIN tarifa_impuesto t ON t.id = r.tarifa_impuesto_id
              LEFT JOIN existencia e ON e.referencia_id = r.id
            """;
    private static final String GROUP = " GROUP BY r.id, p.id, m.nombre, u.codigo, t.porcentaje ";

    private final JdbcClient jdbc;

    public CatalogoConsultas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReferenciaResumen> buscar(String q, Integer categoriaId, boolean soloBajoMinimo, int limite) {
        String texto = q == null ? "" : q.trim();
        String sql = SELECT_RESUMEN + """
                 WHERE (:texto = '' OR r.codigo_barras = :texto OR p.codigo_interno = :texto
                        OR lower(p.nombre || ' ' || m.nombre || ' ' || r.presentacion) LIKE '%' || lower(:texto) || '%')
                   AND (CAST(:cat AS integer) IS NULL OR p.categoria_id = CAST(:cat AS integer))
                """ + GROUP + (soloBajoMinimo
                ? " HAVING r.stock_minimo IS NOT NULL AND coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DISPONIBLE'), 0) < r.stock_minimo "
                : "") + " ORDER BY p.nombre, m.nombre, r.presentacion LIMIT :lim";
        return jdbc.sql(sql).param("texto", texto).param("cat", categoriaId).param("lim", Math.min(limite, 500))
                .query(ReferenciaResumen.class).list().stream().map(CatalogoConsultas::ocultarCosto).toList();
    }

    public ReferenciaResumen porId(Integer id) {
        return jdbc.sql(SELECT_RESUMEN + " WHERE r.id = :id " + GROUP).param("id", id)
                .query(ReferenciaResumen.class).optional().map(CatalogoConsultas::ocultarCosto)
                .orElseThrow(() -> new NoEncontradoException("Referencia", id));
    }

    public ReferenciaResumen porCodigoBarras(String codigo) {
        return jdbc.sql(SELECT_RESUMEN + " WHERE r.codigo_barras = :c " + GROUP).param("c", codigo)
                .query(ReferenciaResumen.class).optional().map(CatalogoConsultas::ocultarCosto)
                .orElseThrow(() -> new NoEncontradoException("Código de barras", codigo));
    }

    public DetalleReferencia detalle(Integer id) {
        ReferenciaResumen r = porId(id);
        List<UnidadReferencia> unidades = jdbc.sql("""
                SELECT u.codigo AS unidad, ru.factor_a_base AS factor, ru.uso_compra, ru.uso_venta
                  FROM referencia_unidad ru JOIN unidad_medida u ON u.id = ru.unidad_id
                 WHERE ru.referencia_id = :id ORDER BY ru.factor_a_base""").param("id", id)
                .query(UnidadReferencia.class).list();
        List<LoteSaldo> lotes = jdbc.sql("""
                SELECT l.id, l.codigo_lote, l.fecha_vencimiento,
                       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DISPONIBLE'), 0) AS disponible,
                       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'RESERVADA'), 0) AS reservada,
                       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DANADA'), 0) AS danada
                  FROM lote l LEFT JOIN existencia e ON e.lote_id = l.id
                 WHERE l.referencia_id = :id GROUP BY l.id ORDER BY l.fecha_vencimiento""").param("id", id)
                .query(LoteSaldo.class).list();
        return new DetalleReferencia(r, unidades, lotes);
    }

    public List<Map<String, Object>> lotesPorVencer(int dias) {
        return jdbc.sql("SELECT * FROM v_lotes_por_vencer WHERE dias_para_vencer <= :d ORDER BY fecha_vencimiento")
                .param("d", dias).query().listOfRows();
    }

    public Map<String, Object> maestros() {
        return Map.of(
                "categorias", jdbc.sql("SELECT id, nombre, padre_id FROM categoria ORDER BY nombre").query().listOfRows(),
                "marcas", jdbc.sql("SELECT id, nombre FROM marca WHERE activo ORDER BY nombre").query().listOfRows(),
                "unidades", jdbc.sql("SELECT id, codigo, nombre, magnitud FROM unidad_medida ORDER BY id").query().listOfRows(),
                "tarifas", jdbc.sql("SELECT id, codigo, porcentaje FROM tarifa_impuesto ORDER BY id").query().listOfRows());
    }

    private static ReferenciaResumen ocultarCosto(ReferenciaResumen r) {
        if (UsuarioActual.tienePermiso("COSTO_VER")) return r;
        return new ReferenciaResumen(r.id(), r.codigoBarras(), r.codigoInterno(), r.producto(), r.marca(), r.presentacion(),
                r.unidadBase(), r.disponible(), r.reservada(), r.danada(), r.precioVenta(), r.porcentajeIva(),
                r.porcentajeIncremento(), null, r.stockMinimo(), r.controlaVencimiento(), r.permiteFraccion(),
                r.fraccionMinima(), r.mesesGarantia(), r.activo(), r.productoId());
    }
}
