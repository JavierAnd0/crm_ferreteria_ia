package co.ferreteria.inventario.catalogo;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** Producto genérico («pintura vinilo blanca»); se vende a través de sus referencias. */
@Entity
@Table(name = "producto")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "codigo_interno")
    private String codigoInterno;
    private String nombre;
    private String descripcion;
    @Column(name = "categoria_id")
    private Integer categoriaId;
    @Column(name = "controla_vencimiento")
    private boolean controlaVencimiento;
    private boolean activo = true;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL)
    private List<Referencia> referencias = new ArrayList<>();

    protected Producto() {
    }

    public Producto(String codigoInterno, String nombre, String descripcion, Integer categoriaId, boolean controlaVencimiento) {
        this.codigoInterno = codigoInterno;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoriaId = categoriaId;
        this.controlaVencimiento = controlaVencimiento;
    }

    public void agregar(Referencia r) {
        r.setProducto(this);
        referencias.add(r);
    }

    public Integer getId() { return id; }
    public String getCodigoInterno() { return codigoInterno; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer categoriaId) { this.categoriaId = categoriaId; }
    public boolean isControlaVencimiento() { return controlaVencimiento; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<Referencia> getReferencias() { return referencias; }
}
