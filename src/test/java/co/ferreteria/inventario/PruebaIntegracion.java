package co.ferreteria.inventario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: PostgreSQL 16 real (Testcontainers) con las migraciones de Flyway
 * y los datos de demostración (perfil demo). El contenedor se comparte entre clases para que el contexto
 * de Spring se reutilice; por eso cada prueba mide diferencias de saldo en lugar de valores absolutos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
public abstract class PruebaIntegracion {

    protected static final String CLAVE_DEMO = "Demo123*";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ferreteria").withUsername("ferreteria").withPassword("ferreteria");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=ferreteria&stringtype=unspecified");
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcClient jdbc;

    private final Map<String, String> tokens = new HashMap<>();

    protected String token(String login) throws Exception {
        if (!tokens.containsKey(login)) {
            String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("login", login, "contrasena", CLAVE_DEMO))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            tokens.put(login, json.readTree(r).get("token").asText());
        }
        return tokens.get(login);
    }

    protected ResultActions enviar(String login, String ruta, Object cuerpo) throws Exception {
        return mvc.perform(con(login, post(ruta)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    protected ResultActions consultar(String login, String ruta) throws Exception {
        return mvc.perform(con(login, get(ruta)));
    }

    protected MockHttpServletRequestBuilder con(String login, MockHttpServletRequestBuilder b) throws Exception {
        return b.header("Authorization", "Bearer " + token(login));
    }

    protected JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    // ---- Lecturas directas de la base para verificar efectos.

    protected int referencia(String codigoBarras) {
        return jdbc.sql("SELECT id FROM referencia WHERE codigo_barras = :c").param("c", codigoBarras).query(Integer.class).single();
    }

    protected long tercero(String identificacion) {
        return jdbc.sql("SELECT id FROM tercero WHERE numero_identificacion = :n").param("n", identificacion).query(Long.class).single();
    }

    /** Cantidad en unidad base de una referencia en un estado (DISPONIBLE, RESERVADA, DANADA). */
    protected BigDecimal saldo(String codigoBarras, String estado) {
        return jdbc.sql("""
                        SELECT coalesce(sum(e.cantidad), 0) FROM existencia e JOIN referencia r ON r.id = e.referencia_id
                         WHERE r.codigo_barras = :c AND e.estado = CAST(:e AS estado_inventario)""")
                .param("c", codigoBarras).param("e", estado).query(BigDecimal.class).single();
    }
}
