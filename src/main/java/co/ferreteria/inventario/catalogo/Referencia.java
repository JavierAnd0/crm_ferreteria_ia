package co.ferreteria.inventario.catalogo;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Referencia vendible (SKU) = producto + marca + presentación, con un solo código de barras.
 * Costos y precio vigente los mantiene la base de datos (kárdex y fn_actualizar_precio),
 * por eso son de solo lectura para JPA.
 */
@Entity
@Table(name = "referencia")
public class Referencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(insertable = false, updatable = false)
    private UUID uuid;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;
    @Column(name = "marca_id")
    private Integer marcaId;
    private String presentacion;
    @Column(name = "codigo_barras")
    private String codigoBarras;
    @Column(name = "unidad_base_id")
    private Short unidadBaseId;
    @Column(name = "permite_fraccion")
    private boolean permiteFraccion;
    @Column(name = "fraccion_minima")
    private BigDecimal fraccionMinima = BigDecimal.ONE;
    @Column(name = "meses_garantia")
    private Short mesesGarantia;
    @Column(name = "tarifa_impuesto_id")
    private Short tarifaImpuestoId;
    @Column(name = "porcentaje_incremento", updatable = false)
    private BigDecimal porcentajeIncremento = BigDecimal.ZERO;
    @Column(name = "multiplo_redondeo")
    private BigDecimal multiploRedondeo = new BigDecimal("50");
    @Column(name = "ultimo_costo", insertable = false, updatable = false)
    private BigDecimal ultimoCosto;
    @Column(name = "costo_promedio", insertable = false, updatable = false)
    private BigDecimal costoPromedio;
    @Column(name = "precio_venta", insertable = false, updatable = false)
    private BigDecimal precioVenta;
    @Column(name = "stock_minimo")
    private BigDecimal stockMinimo;
    private boolean activo = true;

    @OneToMany(mappedBy = "referencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReferenciaUnidad> unidades = new ArrayList<>();

    protected Referencia() {
    }

    public Referencia(Integer marcaId, String presentacion, String codigoBarras, Short unidadBaseId, boolean permiteFraccion,
                      BigDecimal fraccionMinima, Short mesesGarantia, Short tarifaImpuestoId, BigDecimal porcentajeIncremento,
                      BigDecimal stockMinimo) {
        this.marcaId = marcaId;
        this.presentacion = presentacion;
        this.codigoBarras = codigoBarras;
        this.unidadBaseId = unidadBaseId;
        this.permiteFraccion = permiteFraccion;
        this.fraccionMinima = fraccionMinima == null ? BigDecimal.ONE : fraccionMinima;
        this.mesesGarantia = mesesGarantia;
        this.tarifaImpuestoId = tarifaImpuestoId;
        this.porcentajeIncremento = porcentajeIncremento == null ? BigDecimal.ZERO : porcentajeIncremento;
        this.stockMinimo = stockMinimo;
        agregarUnidad(unidadBaseId, BigDecimal.ONE, true, true);
    }

    public void agregarUnidad(Short unidadId, BigDecimal factor, boolean compra, boolean venta) {
        unidades.add(new ReferenciaUnidad(this, unidadId, factor, compra, venta));
    }

    void setProducto(Producto producto) { this.producto = producto; }
    public Integer getId() { return id; }
    public Producto getProducto() { return producto; }
    public Integer getMarcaId() { return marcaId; }
    public String getPresentacion() { return presentacion; }
    public String getCodigoBarras() { return codigoBarras; }
    public Short getUnidadBaseId() { return unidadBaseId; }
    public boolean isPermiteFraccion() { return permiteFraccion; }
    public BigDecimal getFraccionMinima() { return fraccionMinima; }
    public Short getMesesGarantia() { return mesesGarantia; }
    public void setMesesGarantia(Short mesesGarantia) { this.mesesGarantia = mesesGarantia; }
    public Short getTarifaImpuestoId() { return tarifaImpuestoId; }
    public BigDecimal getPorcentajeIncremento() { return porcentajeIncremento; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<ReferenciaUnidad> getUnidades() { return unidades; }
}
