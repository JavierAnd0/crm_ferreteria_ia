package co.ferreteria.inventario.seguridad;

import co.ferreteria.inventario.comun.Fechas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AutenticacionController {

    public record Credenciales(@NotBlank String login, @NotBlank String contrasena) {}
    public record Sesion(String token, String login, String nombre, List<String> permisos, Instant expira) {}

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final PropiedadesJwt props;

    public AutenticacionController(UsuarioRepository usuarios, PasswordEncoder encoder, JwtEncoder jwt, PropiedadesJwt props) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    @PostMapping("/login")
    @Transactional
    public Sesion login(@Valid @RequestBody Credenciales c) {
        Usuario u = usuarios.findByLogin(c.login())
                .filter(Usuario::isActivo)
                .filter(x -> encoder.matches(c.contrasena(), x.getHashContrasena()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos"));
        u.setUltimoAcceso(Fechas.ahora());
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(props.horasValidez(), ChronoUnit.HOURS);
        List<String> permisos = List.copyOf(u.permisos());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("inventario-ferreteria")
                .subject(u.getLogin())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("uid", u.getId())
                .claim("nombre", u.getNombreCompleto())
                .claim("permisos", permisos)
                .build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Sesion(token, u.getLogin(), u.getNombreCompleto(), permisos, expira);
    }
}
