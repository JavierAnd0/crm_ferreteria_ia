package co.ferreteria.inventario.seguridad;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(insertable = false, updatable = false)
    private UUID uuid;
    private String login;
    @Column(name = "nombre_completo")
    private String nombreCompleto;
    private String correo;
    @Column(name = "hash_contrasena")
    private String hashContrasena;
    private boolean activo = true;
    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;
    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new HashSet<>();

    protected Usuario() {
    }

    public Usuario(String login, String nombreCompleto, String correo, String hashContrasena) {
        this.login = login;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.hashContrasena = hashContrasena;
    }

    public Set<String> permisos() {
        Set<String> codigos = new TreeSet<>();
        roles.forEach(r -> r.getPermisos().forEach(p -> codigos.add(p.getCodigo())));
        return codigos;
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getHashContrasena() { return hashContrasena; }
    public void setHashContrasena(String hashContrasena) { this.hashContrasena = hashContrasena; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public void setUltimoAcceso(OffsetDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }
    public Set<Rol> getRoles() { return roles; }
}
