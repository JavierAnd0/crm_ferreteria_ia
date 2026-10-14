package co.ferreteria.inventario.integracion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Patrón outbox (RF-INT-001): el evento se guarda en la misma transacción que la operación;
 * el despachador lo envía después con reintentos. Si la transacción falla, no hay evento.
 */
@Component
public class BandejaSalida {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public BandejaSalida(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void registrar(String tipoEvento, String entidad, long entidadId, Map<String, ?> carga) {
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(carga);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.sql("""
                        INSERT INTO evento_integracion (sistema_id, direccion, tipo_evento, entidad, entidad_id, carga)
                        SELECT id, 'SALIDA', :tipo, :entidad, :id, CAST(:carga AS jsonb)
                          FROM sistema_externo WHERE activo AND endpoint_url IS NOT NULL""")
                .param("tipo", tipoEvento).param("entidad", entidad).param("id", entidadId).param("carga", cuerpo)
                .update();
    }
}
