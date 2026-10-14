package co.ferreteria.inventario.precios;

import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * RF-PRE-001. Precio = último costo × (1 + porcentaje / 100), redondeado hacia arriba al múltiplo
 * de la referencia (fn_calcular_precio). Cada cambio cierra la versión vigente en precio_historial.
 */
@Service
public class PrecioService {

    private final JdbcClient jdbc;

    public PrecioService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BigDecimal cambiarPorcentaje(int referenciaId, BigDecimal porcentaje) {
        if (porcentaje.signum() < 0) throw new NegocioException("El porcentaje no puede ser negativo");
        int filas = jdbc.sql("UPDATE referencia SET porcentaje_incremento = :p WHERE id = :id")
                .param("p", porcentaje).param("id", referenciaId).update();
        if (filas == 0) throw new NoEncontradoException("Referencia", referenciaId);
        return jdbc.sql("SELECT fn_actualizar_precio(:r, 'CAMBIO_PORCENTAJE', :u)")
                .param("r", referenciaId).param("u", UsuarioActual.id()).query(BigDecimal.class).single();
    }

    @Transactional
    public void fijarPrecioManual(int referenciaId, BigDecimal precio, String motivo) {
        if (precio.signum() <= 0) throw new NegocioException("El precio debe ser mayor que cero");
        record Ref(BigDecimal ultimoCosto, BigDecimal porcentajeIncremento, BigDecimal multiploRedondeo) {}
        Ref r = jdbc.sql("SELECT ultimo_costo, porcentaje_incremento, multiplo_redondeo FROM referencia WHERE id = :id FOR UPDATE")
                .param("id", referenciaId).query(Ref.class).optional()
                .orElseThrow(() -> new NoEncontradoException("Referencia", referenciaId));
        jdbc.sql("UPDATE precio_historial SET vigente_hasta = clock_timestamp() WHERE referencia_id = :r AND vigente_hasta IS NULL")
                .param("r", referenciaId).update();
        jdbc.sql("""
                        INSERT INTO precio_historial (referencia_id, costo_base, porcentaje_incremento, precio_calculado, precio_final, motivo, usuario_id)
                        VALUES (:r, :c, :p, fn_calcular_precio(:c, :p, :m), :precio, 'MANUAL', :u)""")
                .param("r", referenciaId).param("c", r.ultimoCosto()).param("p", r.porcentajeIncremento())
                .param("m", r.multiploRedondeo()).param("precio", precio).param("u", UsuarioActual.id()).update();
        jdbc.sql("UPDATE referencia SET precio_venta = :p WHERE id = :id").param("p", precio).param("id", referenciaId).update();
    }
}
