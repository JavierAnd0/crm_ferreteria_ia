package co.ferreteria.inventario.comun;

import co.ferreteria.inventario.seguridad.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Antes de cada método de servicio (o de controlador marcado @Transactional) fija, para la transacción en curso,
 * quién opera, desde qué equipo y con qué origen. Los disparadores de auditoría de la
 * base de datos leen estos valores (app.usuario_id, app.dispositivo_id, app.origen).
 */
@Aspect
@Component
@Order(10)
public class ContextoAuditoria {

    public static final String CABECERA_DISPOSITIVO = "X-Dispositivo-Id";
    private static final ThreadLocal<String> ORIGEN = new ThreadLocal<>();

    private final JdbcClient jdbc;

    public ContextoAuditoria(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Permite marcar operaciones como FUERA_DE_LINEA, MIGRACION o INTEGRACION. */
    public static <T> T conOrigen(String origen, java.util.function.Supplier<T> accion) {
        String anterior = ORIGEN.get();
        ORIGEN.set(origen);
        try {
            return accion.get();
        } finally {
            if (anterior == null) ORIGEN.remove(); else ORIGEN.set(anterior);
        }
    }

    public static String origenActual() {
        return ORIGEN.get() == null ? "EN_LINEA" : ORIGEN.get();
    }

    @Before("within(co.ferreteria.inventario..*) && (@within(org.springframework.stereotype.Service)"
            + " || (@within(org.springframework.web.bind.annotation.RestController)"
            + " && @annotation(org.springframework.transaction.annotation.Transactional)))")
    public void fijarContexto() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        Long usuario = UsuarioActual.idOpcional().orElse(null);
        jdbc.sql("SELECT set_config('app.usuario_id', :u, true), set_config('app.dispositivo_id', :d, true), "
                        + "set_config('app.origen', :o, true)")
                .param("u", usuario == null ? "" : usuario.toString())
                .param("d", dispositivo())
                .param("o", origenActual())
                .query().singleRow();
    }

    private static String dispositivo() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String valor = req.getHeader(CABECERA_DISPOSITIVO);
            if (valor != null && valor.matches("\\d{1,5}")) return valor;
        }
        return "";
    }
}
