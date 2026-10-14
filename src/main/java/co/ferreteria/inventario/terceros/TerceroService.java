package co.ferreteria.inventario.terceros;

import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-TER-001. Reglas RN-13 (persona natural: nombre y apellido) y RN-14 (persona jurídica: NIT con
 * dígito de verificación válido y razón social). Proveedores requieren dirección, teléfono y correo.
 */
@Service
public class TerceroService {

    public record DatosTercero(@NotBlank @Pattern(regexp = "NATURAL|JURIDICA") String tipoPersona,
                               @NotBlank @Pattern(regexp = "CC|CE|NIT|PAS") String tipoIdentificacion,
                               @NotBlank @Pattern(regexp = "[0-9A-Za-z]{3,20}") String numeroIdentificacion,
                               @Min(0) @Max(9) Short digitoVerificacion,
                               String nombres, String apellidos, String razonSocial,
                               String direccion, String municipio, String telefono, @Email String correo,
                               boolean esCliente, boolean esProveedor) {}

    private final TerceroRepository terceros;

    public TerceroService(TerceroRepository terceros) {
        this.terceros = terceros;
    }

    @Transactional
    public Tercero crear(DatosTercero d) {
        validar(d);
        terceros.findByTipoIdentificacionAndNumeroIdentificacion(d.tipoIdentificacion(), d.numeroIdentificacion())
                .ifPresent(t -> {
                    throw new NegocioException("Ya existe un tercero con " + d.tipoIdentificacion() + " " + d.numeroIdentificacion());
                });
        Tercero t = new Tercero(d.tipoPersona(), d.tipoIdentificacion(), d.numeroIdentificacion());
        aplicar(t, d);
        return terceros.save(t);
    }

    @Transactional
    public Tercero actualizar(Long id, DatosTercero d) {
        Tercero t = terceros.findById(id).orElseThrow(() -> new NoEncontradoException("Tercero", id));
        if (!t.getTipoIdentificacion().equals(d.tipoIdentificacion()) || !t.getNumeroIdentificacion().equals(d.numeroIdentificacion()))
            throw new NegocioException("La identificación de un tercero no se puede cambiar");
        validar(d);
        aplicar(t, d);
        return t;
    }

    /** Validación reutilizada por la migración desde Excel. */
    public static void validarPublico(DatosTercero d) {
        validar(d);
    }

    static void validar(DatosTercero d) {
        if (!d.esCliente() && !d.esProveedor()) throw new NegocioException("El tercero debe ser cliente, proveedor o ambos");
        if ("NATURAL".equals(d.tipoPersona())) {
            if (vacio(d.nombres()) || vacio(d.apellidos()))
                throw new NegocioException("Persona natural: nombre y apellido son obligatorios");
        } else {
            if (!"NIT".equals(d.tipoIdentificacion())) throw new NegocioException("Persona jurídica: se identifica con NIT");
            if (vacio(d.razonSocial())) throw new NegocioException("Persona jurídica: la razón social es obligatoria");
        }
        if ("NIT".equals(d.tipoIdentificacion())
                && !DigitoVerificacion.esValido(d.numeroIdentificacion(), d.digitoVerificacion() == null ? null : d.digitoVerificacion().intValue()))
            throw new NegocioException("El dígito de verificación del NIT no es válido");
        if (d.esProveedor() && (vacio(d.direccion()) || vacio(d.telefono()) || vacio(d.correo())))
            throw new NegocioException("Proveedor: dirección, teléfono y correo son obligatorios");
    }

    private static void aplicar(Tercero t, DatosTercero d) {
        t.setDigitoVerificacion(d.digitoVerificacion());
        t.setNombres(limpio(d.nombres()));
        t.setApellidos(limpio(d.apellidos()));
        t.setRazonSocial(limpio(d.razonSocial()));
        t.setDireccion(limpio(d.direccion()));
        t.setMunicipio(limpio(d.municipio()));
        t.setTelefono(limpio(d.telefono()));
        t.setCorreo(limpio(d.correo()));
        t.setEsCliente(d.esCliente());
        t.setEsProveedor(d.esProveedor());
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim();
    }
}
