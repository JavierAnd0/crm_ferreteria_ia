package co.ferreteria.inventario.comun;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduce errores a respuestas RFC 7807. Las reglas que protege la base de datos
 * (saldo insuficiente, período cerrado, lote vencido, duplicados) llegan como PSQLException.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(NegocioException.class)
    ProblemDetail negocio(NegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", e.getMessage());
    }

    @ExceptionHandler(NoEncontradoException.class)
    ProblemDetail noEncontrado(NoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", detalle);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesoDenegado(AccessDeniedException e) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tiene permiso para esta operación");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail baseDatos(DataAccessException e) {
        Throwable causa = e.getMostSpecificCause();
        if (causa instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
            String estado = psql.getSQLState();
            String mensaje = psql.getServerErrorMessage().getMessage();
            return switch (estado) {
                case "23505" -> problema(HttpStatus.CONFLICT, "Registro duplicado", mensaje);
                case "23514", "23503", "23502" -> problema(HttpStatus.CONFLICT, "Restricción de datos", mensaje);
                case "P0001" -> problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", mensaje);
                default -> {
                    log.error("Error de base de datos {}", estado, e);
                    yield problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error de base de datos", "Error interno");
                }
            };
        }
        log.error("Error de acceso a datos", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error de base de datos", "Error interno");
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        return p;
    }
}
