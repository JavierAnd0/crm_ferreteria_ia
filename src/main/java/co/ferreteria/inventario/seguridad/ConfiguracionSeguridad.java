package co.ferreteria.inventario.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * API sin estado protegida con JWT (HS256). Los permisos del usuario viajan en el claim
 * «permisos» y se convierten en autoridades sin prefijo (p. ej. VENTA_EMITIR).
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    private static final String RUTA_INGRESO = "/api/auth/login";

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint sinSesion = sinSesion(json);
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, RUTA_INGRESO).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())   // interfaz web estática, Swagger y health
                .exceptionHandling(e -> e.authenticationEntryPoint(sinSesion))
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenSalvoAlIngresar())
                        .authenticationEntryPoint(sinSesion)
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor())));
        return http.build();
    }

    /** Al ingresar se ignora el token guardado en el navegador: si venció o se firmó con otra clave, bloquearía el ingreso. */
    private static BearerTokenResolver tokenSalvoAlIngresar() {
        DefaultBearerTokenResolver normal = new DefaultBearerTokenResolver();
        return req -> RUTA_INGRESO.equals(req.getRequestURI()) ? null : normal.resolve(req);
    }

    /** 401 con cuerpo RFC 7807 (además de la cabecera WWW-Authenticate) para que la interfaz muestre un mensaje. */
    private static AuthenticationEntryPoint sinSesion(ObjectMapper json) {
        BearerTokenAuthenticationEntryPoint cabecera = new BearerTokenAuthenticationEntryPoint();
        return (req, res, ex) -> {
            cabecera.commence(req, res, ex);
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "La sesión terminó o no es válida. Ingrese de nuevo.");
            p.setTitle("Sin sesión");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getOutputStream(), p);
        };
    }

    private JwtAuthenticationConverter convertidor() {
        JwtGrantedAuthoritiesConverter permisos = new JwtGrantedAuthoritiesConverter();
        permisos.setAuthoritiesClaimName("permisos");
        permisos.setAuthorityPrefix("");
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(permisos);
        return c;
    }

    @Bean
    SecretKey claveJwt(PropiedadesJwt props) {
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave) {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
