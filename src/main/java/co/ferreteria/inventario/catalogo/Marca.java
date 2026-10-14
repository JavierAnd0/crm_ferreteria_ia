package co.ferreteria.inventario.catalogo;

import jakarta.persistence.*;

@Entity
@Table(name = "marca")
public class Marca {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private boolean activo = true;

    protected Marca() {
    }

    public Marca(String nombre) {
        this.nombre = nombre;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isActivo() { return activo; }
}
