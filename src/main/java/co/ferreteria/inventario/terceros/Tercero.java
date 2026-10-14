package co.ferreteria.inventario.terceros;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "tercero")
public class Tercero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(insertable = false, updatable = false)
    private UUID uuid;
    @Column(name = "tipo_persona")
    private String tipoPersona;
    @Column(name = "tipo_identificacion")
    private String tipoIdentificacion;
    @Column(name = "numero_identificacion")
    private String numeroIdentificacion;
    @Column(name = "digito_verificacion")
    private Short digitoVerificacion;
    private String nombres;
    private String apellidos;
    @Column(name = "razon_social")
    private String razonSocial;
    private String direccion;
    private String municipio;
    private String telefono;
    private String correo;
    @Column(name = "es_cliente")
    private boolean esCliente;
    @Column(name = "es_proveedor")
    private boolean esProveedor;
    private boolean activo = true;

    protected Tercero() {
    }

    public Tercero(String tipoPersona, String tipoIdentificacion, String numeroIdentificacion) {
        this.tipoPersona = tipoPersona;
        this.tipoIdentificacion = tipoIdentificacion;
        this.numeroIdentificacion = numeroIdentificacion;
    }

    public String nombreMostrar() {
        return razonSocial != null ? razonSocial : (nombres + " " + apellidos);
    }

    public Long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getTipoPersona() { return tipoPersona; }
    public String getTipoIdentificacion() { return tipoIdentificacion; }
    public String getNumeroIdentificacion() { return numeroIdentificacion; }
    public Short getDigitoVerificacion() { return digitoVerificacion; }
    public void setDigitoVerificacion(Short dv) { this.digitoVerificacion = dv; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public boolean isEsCliente() { return esCliente; }
    public void setEsCliente(boolean esCliente) { this.esCliente = esCliente; }
    public boolean isEsProveedor() { return esProveedor; }
    public void setEsProveedor(boolean esProveedor) { this.esProveedor = esProveedor; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
