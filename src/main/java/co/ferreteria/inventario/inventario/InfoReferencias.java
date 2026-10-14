package co.ferreteria.inventario.inventario;

import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Datos de una referencia necesarios para comprar o vender, y resolución de lotes. */
@Component
public class InfoReferencias {

    public record Info(int id, String nombre, boolean activo, boolean controlaVencimiento, boolean permiteFraccion,
                       BigDecimal fraccionMinima, BigDecimal precioVenta, BigDecimal porcentajeIva, Short mesesGarantia) {}

    public record Unidad(short unidadId, BigDecimal factor) {}

    private final JdbcClient jdbc;

    public InfoReferencias(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Info info(int referenciaId) {
        return jdbc.sql("""
                        SELECT r.id, p.nombre || ' ' || m.nombre || ' ' || r.presentacion AS nombre, r.activo AND p.activo AS activo,
                               p.controla_vencimiento, r.permite_fraccion, r.fraccion_minima, r.precio_venta,
                               t.porcentaje AS porcentaje_iva, r.meses_garantia
                          FROM referencia r JOIN producto p ON p.id = r.producto_id JOIN marca m ON m.id = r.marca_id
                          JOIN tarifa_impuesto t ON t.id = r.tarifa_impuesto_id
                         WHERE r.id = :id""")
                .param("id", referenciaId)
                .query(Info.class).optional()
                .orElseThrow(() -> new NoEncontradoException("Referencia", referenciaId));
    }

    /** Factor de la unidad indicada; null o vacío = unidad base. */
    public Unidad unidad(int referenciaId, String codigoUnidad, boolean paraVenta) {
        String sql = """
                SELECT ru.unidad_id, ru.factor_a_base FROM referencia_unidad ru
                  JOIN unidad_medida u ON u.id = ru.unidad_id JOIN referencia r ON r.id = ru.referencia_id
                 WHERE ru.referencia_id = :r
                   AND (CASE WHEN :c = '' THEN ru.unidad_id = r.unidad_base_id ELSE u.codigo = :c END)
                   AND (CASE WHEN :venta THEN ru.uso_venta ELSE ru.uso_compra END)""";
        String codigo = codigoUnidad == null ? "" : codigoUnidad.toUpperCase();
        return jdbc.sql(sql).param("r", referenciaId).param("c", codigo).param("venta", paraVenta)
                .query((rs, n) -> new Unidad(rs.getShort(1), rs.getBigDecimal(2))).optional()
                .orElseThrow(() -> new NegocioException("La unidad " + (codigo.isEmpty() ? "base" : codigo)
                        + " no está habilitada para " + (paraVenta ? "venta" : "compra") + " en la referencia " + referenciaId));
    }

    /** Busca el lote por código o lo crea; si existe, la fecha de vencimiento debe coincidir. */
    public long loteParaCompra(int referenciaId, String codigo, LocalDate vence, LocalDate fabricado) {
        if (codigo == null || codigo.isBlank() || vence == null)
            throw new NegocioException("La referencia " + referenciaId + " controla vencimiento: indique lote y fecha de vencimiento");
        Optional<Object[]> existente = jdbc.sql("SELECT id, fecha_vencimiento FROM lote WHERE referencia_id = :r AND codigo_lote = :c")
                .param("r", referenciaId).param("c", codigo.trim())
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getObject(2, LocalDate.class)}).optional();
        if (existente.isPresent()) {
            if (!vence.equals(existente.get()[1]))
                throw new NegocioException("El lote " + codigo + " ya existe con otra fecha de vencimiento");
            return (Long) existente.get()[0];
        }
        return jdbc.sql("""
                        INSERT INTO lote (referencia_id, codigo_lote, fecha_fabricacion, fecha_vencimiento)
                        VALUES (:r, :c, :f, :v) RETURNING id""")
                .param("r", referenciaId).param("c", codigo.trim()).param("f", fabricado).param("v", vence)
                .query(Long.class).single();
    }

    /** Lote que vence primero, vigente y con saldo suficiente en el estado indicado (FEFO). */
    public long loteFefo(int referenciaId, String estado, BigDecimal cantidadBase, LocalDate fecha) {
        return jdbc.sql("""
                        SELECT l.id FROM lote l JOIN existencia e ON e.lote_id = l.id
                         WHERE l.referencia_id = :r AND e.estado = CAST(:estado AS estado_inventario)
                           AND e.cantidad >= :cant AND l.fecha_vencimiento >= :hoy
                         ORDER BY l.fecha_vencimiento, l.id LIMIT 1""")
                .param("r", referenciaId).param("estado", estado).param("cant", cantidadBase).param("hoy", fecha)
                .query(Long.class).optional()
                .orElseThrow(() -> new NegocioException("No hay un lote vigente con saldo suficiente para la referencia " + referenciaId));
    }
}
