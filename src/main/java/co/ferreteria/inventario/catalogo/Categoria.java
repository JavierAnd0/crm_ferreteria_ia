package co.ferreteria.inventario.catalogo;

import jakarta.persistence.*;

@Entity
@Table(name = "categoria")
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    @Column(name = "padre_id")
    private Integer padreId;

    protected Categoria() {
    }

    public Categoria(String nombre, Integer padreId) {
        this.nombre = nombre;
        this.padreId = padreId;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Integer getPadreId() { return padreId; }
}
