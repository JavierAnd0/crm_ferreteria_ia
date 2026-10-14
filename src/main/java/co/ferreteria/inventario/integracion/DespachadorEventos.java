package co.ferreteria.inventario.integracion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Envía los eventos pendientes por HTTP POST con la cabecera Idempotency-Key. Reintenta con
 * espera exponencial (2^intentos minutos) y, tras el máximo de intentos, deja el evento en ERROR.
 * FOR UPDATE SKIP LOCKED permite varias instancias sin envíos duplicados.
 */
@Component
public class DespachadorEventos {

    private static final Logger log = LoggerFactory.getLogger(DespachadorEventos.class);

    record Pendiente(long id, UUID clave, String url, String tipoEvento, String carga, int intentos) {}

    private final JdbcClient jdbc;
    private final RestClient http;
    private final int maxIntentos;

    public DespachadorEventos(JdbcClient jdbc, RestClient.Builder builder, @Value("${app.integracion.max-intentos:8}") int maxIntentos) {
        this.jdbc = jdbc;
        // Tiempos de espera acotados: un sistema externo lento no debe retener los eventos bloqueados.
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(15));
        this.http = builder.requestFactory(fabrica).build();
        this.maxIntentos = maxIntentos;
    }

    @Scheduled(fixedDelayString = "${app.integracion.intervalo-ms:30000}", initialDelay = 20000)
    @Transactional
    public void despachar() {
        List<Pendiente> pendientes = jdbc.sql("""
                        SELECT e.id, e.clave_idempotencia, s.endpoint_url, e.tipo_evento, e.carga::text, e.intentos
                          FROM evento_integracion e JOIN sistema_externo s ON s.id = e.sistema_id
                         WHERE e.estado = 'PENDIENTE' AND e.direccion = 'SALIDA' AND e.proximo_intento <= now() AND s.activo
                         ORDER BY e.id LIMIT 50 FOR UPDATE OF e SKIP LOCKED""")
                .query((rs, n) -> new Pendiente(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getInt(6)))
                .list();
        for (Pendiente p : pendientes) {
            try {
                http.post().uri(p.url()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", p.clave().toString())
                        .header("X-Tipo-Evento", p.tipoEvento())
                        .body(p.carga()).retrieve().toBodilessEntity();
                jdbc.sql("UPDATE evento_integracion SET estado = 'ENVIADO', intentos = intentos + 1, ultimo_error = NULL WHERE id = :id")
                        .param("id", p.id()).update();
            } catch (Exception e) {
                int intentos = p.intentos() + 1;
                log.warn("Fallo al enviar evento {} (intento {}): {}", p.id(), intentos, e.getMessage());
                jdbc.sql("""
                                UPDATE evento_integracion
                                   SET intentos = :i, ultimo_error = :err,
                                       estado = CASE WHEN :i >= :max THEN 'ERROR' ELSE 'PENDIENTE' END,
                                       proximo_intento = now() + make_interval(mins => power(2, :i)::int)
                                 WHERE id = :id""")
                        .param("i", intentos).param("err", String.valueOf(e.getMessage())).param("max", maxIntentos)
                        .param("id", p.id()).update();
            }
        }
    }
}
