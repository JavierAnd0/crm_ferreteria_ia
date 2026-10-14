package co.ferreteria.inventario.seguridad;

import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

/** RF-SEG-001: usuarios y roles. Los usuarios no se borran: se desactivan. */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasAuthority('USUARIO_ADMINISTRAR')")
public class UsuarioController {

    public record UsuarioDto(Long id, String login, String nombre, String correo, boolean activo, List<String> roles) {
        static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getLogin(), u.getNombreCompleto(), u.getCorreo(), u.isActivo(),
                    u.getRoles().stream().map(Rol::getCodigo).sorted().toList());
        }
    }

    public record NuevoUsuario(@NotBlank String login, @NotBlank String nombre, String correo,
                               @Size(min = 8) String contrasena, @NotEmpty List<String> roles) {}

    public record CambioUsuario(String nombre, String correo, Boolean activo, List<String> roles,
                                @Size(min = 8) String contrasena) {}

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;

    public UsuarioController(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UsuarioDto> listar() {
        return usuarios.findAll().stream().map(UsuarioDto::de).toList();
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public List<Rol> roles() {
        return roles.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioDto crear(@Valid @RequestBody NuevoUsuario n) {
        if (n.contrasena() == null) throw new NegocioException("La contraseña es obligatoria (mínimo 8 caracteres)");
        Usuario u = new Usuario(n.login().trim().toLowerCase(), n.nombre(), n.correo(), encoder.encode(n.contrasena()));
        u.getRoles().addAll(rolesValidos(n.roles()));
        return UsuarioDto.de(usuarios.save(u));
    }

    @PutMapping("/{id}")
    @Transactional
    public UsuarioDto actualizar(@PathVariable Long id, @Valid @RequestBody CambioUsuario c) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new NoEncontradoException("Usuario", id));
        if (c.nombre() != null) u.setNombreCompleto(c.nombre());
        if (c.correo() != null) u.setCorreo(c.correo());
        if (c.activo() != null) u.setActivo(c.activo());
        if (c.contrasena() != null) u.setHashContrasena(encoder.encode(c.contrasena()));
        if (c.roles() != null) {
            u.getRoles().clear();
            u.getRoles().addAll(rolesValidos(c.roles()));
        }
        return UsuarioDto.de(u);
    }

    private HashSet<Rol> rolesValidos(List<String> codigos) {
        List<Rol> encontrados = roles.findByCodigoIn(codigos);
        if (encontrados.size() != new HashSet<>(codigos).size()) throw new NegocioException("Rol inexistente en " + codigos);
        return new HashSet<>(encontrados);
    }
}
