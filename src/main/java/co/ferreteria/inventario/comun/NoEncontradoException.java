package co.ferreteria.inventario.comun;

public class NoEncontradoException extends RuntimeException {
    public NoEncontradoException(String entidad, Object id) {
        super(entidad + " no encontrado: " + id);
    }
}
