package co.ferreteria.inventario.comun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

/**
 * Acepta la cadena de conexión que entregan Neon, Render y casi todo PostgreSQL administrado
 * ({@code DATABASE_URL=postgresql://usuario:clave@host/base?sslmode=require}) y la traduce a las propiedades JDBC.
 * Si existe {@code DB_URL}, gana {@code DB_URL} y esta clase no hace nada.
 */
public class UrlBaseDatos implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        String url = entorno.getProperty("DATABASE_URL");
        if (url == null || url.isBlank() || entorno.containsProperty("DB_URL")) {
            return;
        }
        entorno.getPropertySources().addFirst(new MapPropertySource("DATABASE_URL", traducir(url)));
    }

    /** Devuelve url, username y password de {@code spring.datasource} a partir de una URL postgres://. */
    static Map<String, Object> traducir(String url) {
        URI uri = URI.create(url.trim());
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DATABASE_URL debe empezar por postgresql://");
        }
        var parametros = new ArrayList<String>();
        if (uri.getRawQuery() != null) {
            for (String parametro : uri.getRawQuery().split("&")) {
                // channel_binding es de libpq; el controlador JDBC no lo reconoce.
                if (!parametro.isBlank() && !parametro.startsWith("channel_binding=")) {
                    parametros.add(parametro);
                }
            }
        }
        parametros.add("currentSchema=ferreteria");
        parametros.add("stringtype=unspecified");

        String puerto = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String jdbc = "jdbc:postgresql://" + uri.getHost() + puerto + uri.getRawPath() + "?" + String.join("&", parametros);

        String usuario = "";
        String clave = "";
        if (uri.getRawUserInfo() != null) {
            String[] partes = uri.getRawUserInfo().split(":", 2);
            usuario = decodificar(partes[0]);
            clave = partes.length > 1 ? decodificar(partes[1]) : "";
        }
        return Map.of("spring.datasource.url", jdbc,
                "spring.datasource.username", usuario,
                "spring.datasource.password", clave);
    }

    private static String decodificar(String texto) {
        return URLDecoder.decode(texto.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
