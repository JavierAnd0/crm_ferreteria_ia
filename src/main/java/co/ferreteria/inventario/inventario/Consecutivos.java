package co.ferreteria.inventario.inventario;

import co.ferreteria.inventario.comun.NegocioException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Numeración de documentos. El UPDATE bloquea la fila: no hay números repetidos ni saltos por concurrencia. */
@Component
public class Consecutivos {

    public record Numero(String prefijo, long numero) {}

    public record Rango(String prefijo, long desde, long hasta) {}

    private final JdbcClient jdbc;

    public Consecutivos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Siguiente número del rango del servidor (dispositivo nulo) para INGRESO o FACTURA. */
    public Numero siguiente(String tipoDocumento) {
        return jdbc.sql("""
                        UPDATE consecutivo SET numero_actual = numero_actual + 1
                         WHERE id = (SELECT id FROM consecutivo
                                      WHERE tipo_documento = :tipo AND dispositivo_id IS NULL AND activo
                                        AND numero_actual < numero_hasta AND (vigente_hasta IS NULL OR vigente_hasta >= current_date)
                                      ORDER BY numero_desde LIMIT 1)
                        RETURNING prefijo, numero_actual""")
                .param("tipo", tipoDocumento)
                .query((rs, n) -> new Numero(rs.getString(1), rs.getLong(2)))
                .optional()
                .orElseThrow(() -> new NegocioException("No hay rango de numeración vigente para " + tipoDocumento));
    }

    /** Reserva un bloque de números de factura para que un equipo facture sin internet (RF-OFF-001). */
    public Rango reservarBloque(short dispositivoId, int cantidad) {
        return jdbc.sql("""
                        UPDATE consecutivo SET numero_actual = numero_actual + :n
                         WHERE tipo_documento = 'FACTURA' AND dispositivo_id = :d AND activo AND numero_actual + :n <= numero_hasta
                        RETURNING prefijo, numero_actual - :n + 1, numero_actual""")
                .param("n", cantidad).param("d", dispositivoId)
                .query((rs, n) -> new Rango(rs.getString(1), rs.getLong(2), rs.getLong(3)))
                .optional()
                .orElseThrow(() -> new NegocioException("El equipo no tiene rango de facturación disponible"));
    }

    /** Verifica que un número de factura usado sin conexión pertenezca a un bloque ya entregado al equipo. */
    public boolean numeroEntregadoAlEquipo(short dispositivoId, String prefijo, long numero) {
        return jdbc.sql("""
                        SELECT count(*) FROM consecutivo
                         WHERE tipo_documento = 'FACTURA' AND dispositivo_id = :d AND prefijo = :p
                           AND :num BETWEEN numero_desde AND numero_actual""")
                .param("d", dispositivoId).param("p", prefijo).param("num", numero)
                .query(Long.class).single() > 0;
    }
}
