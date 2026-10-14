package co.ferreteria.inventario.cierres;

import co.ferreteria.inventario.comun.Fechas;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-CIE-001. fn_cerrar_periodo verifica operaciones sin internet pendientes, fotografía saldos
 * (cierre_saldo) y bloquea el mes; desde entonces el disparador del kárdex rechaza movimientos en él.
 */
@Service
public class CierreService {

    private final JdbcClient jdbc;

    public CierreService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int cerrar(int periodo) {
        if (periodo >= Fechas.periodo(Fechas.ahora())) throw new NegocioException("Solo se cierran meses ya terminados");
        jdbc.sql("SELECT fn_asegurar_periodo(:p)").param("p", periodo).query().singleRow();
        return jdbc.sql("SELECT fn_cerrar_periodo(:p, :u)").param("p", periodo).param("u", UsuarioActual.id())
                .query(Integer.class).single();
    }

    @Transactional
    public void reabrir(int periodo, String motivo) {
        int filas = jdbc.sql("""
                        UPDATE periodo SET estado = 'REABIERTO', reabierto_en = now(), reabierto_por = :u, motivo_reapertura = :m
                         WHERE id = :p AND estado = 'CERRADO'""")
                .param("u", UsuarioActual.id()).param("m", motivo).param("p", periodo).update();
        if (filas == 0) throw new NegocioException("El período " + periodo + " no está cerrado");
        jdbc.sql("INSERT INTO auditoria (usuario_id, tabla, registro_id, accion, despues) VALUES (:u, 'periodo', :p, 'REAPERTURA', jsonb_build_object('motivo', :m))")
                .param("u", UsuarioActual.id()).param("p", String.valueOf(periodo)).param("m", motivo).update();
    }

    /** Crea particiones mensuales del kárdex y la auditoría para el mes en curso y los 6 siguientes. */
    @Transactional
    public int mantenerParticiones() {
        return jdbc.sql("SELECT fn_crear_particiones(current_date, 7)").query(Integer.class).single();
    }
}
