package co.ferreteria.inventario.inventario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Libera automáticamente las reservas vencidas (vigencia configurable, 72 h por defecto). */
@Component
public class TareasInventario {

    private static final Logger log = LoggerFactory.getLogger(TareasInventario.class);
    private final EstadoService estados;

    public TareasInventario(EstadoService estados) {
        this.estados = estados;
    }

    @Scheduled(fixedDelayString = "${app.reservas.revision-ms:900000}", initialDelay = 60000)
    public void liberarReservasVencidas() {
        ComoSistema.ejecutar(() -> {
            for (Long id : estados.reservasVencidas()) {
                try {
                    estados.liberar(id, "VENCIDA", null);
                } catch (RuntimeException e) {
                    log.warn("No se pudo liberar la reserva {}: {}", id, e.getMessage());
                }
            }
        });
    }

    /** Ejecuta tareas programadas con la identidad del usuario técnico (id 1, administrador) para la auditoría. */
    public static final class ComoSistema {
        private ComoSistema() {
        }

        public static void ejecutar(Runnable tarea) {
            Jwt jwt = Jwt.withTokenValue("sistema").header("alg", "none").subject("sistema").claim("uid", 1L)
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
            var auth = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
            var anterior = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                tarea.run();
            } finally {
                SecurityContextHolder.setContext(anterior);
            }
        }
    }
}
