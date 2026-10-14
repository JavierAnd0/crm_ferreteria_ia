package co.ferreteria.inventario.catalogo;

import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** RF-PRO-001 y RF-PRO-003: productos, referencias (marca + presentación) y conversiones. */
@Service
public class CatalogoService {

    public record Conversion(@NotBlank String unidad, @NotNull @Positive BigDecimal factor,
                             boolean usoCompra, boolean usoVenta) {}

    public record NuevaReferencia(@NotNull Integer marcaId, @NotBlank String presentacion,
                                  @NotBlank @Pattern(regexp = "[0-9A-Za-z]{4,14}") String codigoBarras,
                                  @NotBlank String unidadBase, boolean permiteFraccion,
                                  @Positive BigDecimal fraccionMinima, @Positive Short mesesGarantia,
                                  String tarifa, @PositiveOrZero BigDecimal porcentajeIncremento,
                                  @PositiveOrZero BigDecimal stockMinimo, List<@Valid Conversion> conversiones) {}

    public record NuevoProducto(@NotBlank String codigoInterno, @NotBlank String nombre, String descripcion,
                                @NotNull Integer categoriaId, boolean controlaVencimiento,
                                @NotEmpty List<@Valid NuevaReferencia> referencias) {}

    private final ProductoRepository productos;
    private final ReferenciaRepository referencias;
    private final CategoriaRepository categorias;
    private final MarcaRepository marcas;
    private final JdbcClient jdbc;

    public CatalogoService(ProductoRepository productos, ReferenciaRepository referencias, CategoriaRepository categorias,
                           MarcaRepository marcas, JdbcClient jdbc) {
        this.productos = productos;
        this.referencias = referencias;
        this.categorias = categorias;
        this.marcas = marcas;
        this.jdbc = jdbc;
    }

    @Transactional
    public Integer crearProducto(NuevoProducto n) {
        if (productos.findByCodigoInterno(n.codigoInterno()).isPresent())
            throw new NegocioException("Ya existe un producto con código interno " + n.codigoInterno());
        if (!categorias.existsById(n.categoriaId())) throw new NoEncontradoException("Categoría", n.categoriaId());
        Producto p = new Producto(n.codigoInterno(), n.nombre(), n.descripcion(), n.categoriaId(), n.controlaVencimiento());
        n.referencias().forEach(r -> p.agregar(construir(r)));
        return productos.save(p).getId();
    }

    @Transactional
    public Integer agregarReferencia(Integer productoId, NuevaReferencia r) {
        Producto p = productos.findById(productoId).orElseThrow(() -> new NoEncontradoException("Producto", productoId));
        Referencia ref = construir(r);
        p.agregar(ref);
        productos.saveAndFlush(p);
        return ref.getId();
    }

    @Transactional
    public void agregarConversion(Integer referenciaId, Conversion c) {
        Referencia r = referencias.findById(referenciaId).orElseThrow(() -> new NoEncontradoException("Referencia", referenciaId));
        short unidad = unidadId(c.unidad());
        if (r.getUnidades().stream().anyMatch(u -> u.getUnidadId() == unidad))
            throw new NegocioException("La referencia ya tiene la unidad " + c.unidad());
        r.agregarUnidad(unidad, c.factor(), c.usoCompra(), c.usoVenta());
    }

    @Transactional
    public Categoria crearCategoria(String nombre, Integer padreId) {
        return categorias.save(new Categoria(nombre.trim(), padreId));
    }

    @Transactional
    public Marca crearMarca(String nombre) {
        marcas.findByNombreIgnoreCase(nombre.trim()).ifPresent(m -> {
            throw new NegocioException("La marca ya existe");
        });
        return marcas.save(new Marca(nombre.trim()));
    }

    private Referencia construir(NuevaReferencia r) {
        if (referencias.existsByCodigoBarras(r.codigoBarras()))
            throw new NegocioException("El código de barras " + r.codigoBarras() + " ya está asignado a otra referencia");
        if (!marcas.existsById(r.marcaId())) throw new NoEncontradoException("Marca", r.marcaId());
        BigDecimal fraccion = r.fraccionMinima() == null ? BigDecimal.ONE : r.fraccionMinima();
        if (!r.permiteFraccion() && fraccion.compareTo(BigDecimal.ONE) != 0)
            throw new NegocioException("Una referencia sin fracciones debe tener fracción mínima 1");
        short base = unidadId(r.unidadBase());
        Referencia ref = new Referencia(r.marcaId(), r.presentacion().trim(), r.codigoBarras(), base, r.permiteFraccion(),
                fraccion, r.mesesGarantia(), tarifaId(r.tarifa() == null ? "IVA19" : r.tarifa()),
                r.porcentajeIncremento(), r.stockMinimo());
        if (r.conversiones() != null) {
            for (Conversion c : r.conversiones()) {
                short u = unidadId(c.unidad());
                if (u == base) continue;
                ref.agregarUnidad(u, c.factor(), c.usoCompra(), c.usoVenta());
            }
        }
        return ref;
    }

    short unidadId(String codigo) {
        return jdbc.sql("SELECT id FROM unidad_medida WHERE codigo = :c").param("c", codigo.toUpperCase())
                .query(Short.class).optional().orElseThrow(() -> new NegocioException("Unidad inexistente: " + codigo));
    }

    short tarifaId(String codigo) {
        return jdbc.sql("SELECT id FROM tarifa_impuesto WHERE codigo = :c").param("c", codigo.toUpperCase())
                .query(Short.class).optional().orElseThrow(() -> new NegocioException("Tarifa inexistente: " + codigo));
    }
}
