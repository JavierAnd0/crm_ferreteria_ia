package co.ferreteria.inventario.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record PropiedadesJwt(String secret, int horasValidez) {
}
