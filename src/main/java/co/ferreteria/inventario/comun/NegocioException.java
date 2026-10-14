package co.ferreteria.inventario.comun;

/** Regla de negocio incumplida: se responde con 422 y el mensaje para el usuario. */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
