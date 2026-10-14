package co.ferreteria.inventario.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/** Acceso al usuario autenticado (claim «uid» del token). */
public final class UsuarioActual {
    private UsuarioActual() {
    }

    public static Optional<Long> idOpcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getClaim("uid") != null) {
            return Optional.of(((Number) jwt.getClaim("uid")).longValue());
        }
        return Optional.empty();
    }

    public static long id() {
        return idOpcional().orElseThrow(() -> new IllegalStateException("Operación sin usuario autenticado"));
    }

    public static boolean tienePermiso(String permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permiso));
    }
}
