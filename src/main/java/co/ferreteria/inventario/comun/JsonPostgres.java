package co.ferreteria.inventario.comun;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.postgresql.util.PGobject;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

/** Columnas json/jsonb leídas con JdbcClient se devuelven como JSON, no como texto escapado. */
@JsonComponent
public class JsonPostgres extends JsonSerializer<PGobject> {
    @Override
    public void serialize(PGobject valor, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (valor.getValue() == null) {
            gen.writeNull();
        } else if ("json".equals(valor.getType()) || "jsonb".equals(valor.getType())) {
            gen.writeRawValue(valor.getValue());
        } else {
            gen.writeString(valor.getValue());
        }
    }
}
