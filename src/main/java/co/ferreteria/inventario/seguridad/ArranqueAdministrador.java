package co.ferreteria.inventario.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Crea el usuario «admin» en la primera ejecución con la contraseña de ADMIN_PASSWORD. */
@Component
public class ArranqueAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArranqueAdministrador.class);

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final String contrasena;

    public ArranqueAdministrador(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder encoder,
                                 @Value("${app.admin.password:}") String contrasena) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.contrasena = contrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) return;
        if (contrasena == null || contrasena.length() < 8) {
            log.warn("No hay usuarios y ADMIN_PASSWORD no está definida (mínimo 8 caracteres): no se creó el administrador");
            return;
        }
        Usuario admin = new Usuario("admin", "Administrador del sistema", null, encoder.encode(contrasena));
        admin.getRoles().addAll(roles.findByCodigoIn(List.of("ADMIN")));
        usuarios.save(admin);
        log.info("Usuario administrador creado");
    }
}
