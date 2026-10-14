package co.ferreteria.inventario.catalogo;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/** Unidad de compra o venta de una referencia y su factor a la unidad base (RF-PRO-003). */
@Entity
@Table(name = "referencia_unidad")
@IdClass(ReferenciaUnidad.Clave.class)
public class ReferenciaUnidad {

    public static class Clave implements Serializable {
        private Integer referencia;
        private Short unidadId;

        public Clave() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(referencia, c.referencia) && Objects.equals(unidadId, c.unidadId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referencia, unidadId);
        }
    }

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referencia_id")
    private Referencia referencia;
    @Id
    @Column(name = "unidad_id")
    private Short unidadId;
    @Column(name = "factor_a_base")
    private BigDecimal factorABase;
    @Column(name = "uso_compra")
    private boolean usoCompra;
    @Column(name = "uso_venta")
    private boolean usoVenta;

    protected ReferenciaUnidad() {
    }

    ReferenciaUnidad(Referencia referencia, Short unidadId, BigDecimal factorABase, boolean usoCompra, boolean usoVenta) {
        this.referencia = referencia;
        this.unidadId = unidadId;
        this.factorABase = factorABase;
        this.usoCompra = usoCompra;
        this.usoVenta = usoVenta;
    }

    public Short getUnidadId() { return unidadId; }
    public BigDecimal getFactorABase() { return factorABase; }
    public boolean isUsoCompra() { return usoCompra; }
    public boolean isUsoVenta() { return usoVenta; }
}
