package co.ferreteria.inventario.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogoController {

    public record NuevoNombre(@NotBlank String nombre, Integer padreId) {}

    private final CatalogoService servicio;
    private final CatalogoConsultas consultas;

    public CatalogoController(CatalogoService servicio, CatalogoConsultas consultas) {
        this.servicio = servicio;
        this.consultas = consultas;
    }

    @GetMapping("/catalogo/maestros")
    public Map<String, Object> maestros() {
        return consultas.maestros();
    }

    @GetMapping("/referencias")
    public List<CatalogoConsultas.ReferenciaResumen> buscar(@RequestParam(defaultValue = "") String q,
                                                            @RequestParam(required = false) Integer categoriaId,
                                                            @RequestParam(defaultValue = "false") boolean bajoMinimo,
                                                            @RequestParam(defaultValue = "100") int limite) {
        return consultas.buscar(q, categoriaId, bajoMinimo, limite);
    }

    /** RF-LEC-001: el lector de códigos envía el código como texto. */
    @GetMapping("/referencias/codigo/{codigo}")
    public CatalogoConsultas.ReferenciaResumen porCodigo(@PathVariable String codigo) {
        return consultas.porCodigoBarras(codigo);
    }

    @GetMapping("/referencias/{id}")
    public CatalogoConsultas.DetalleReferencia detalle(@PathVariable Integer id) {
        return consultas.detalle(id);
    }

    @GetMapping("/lotes/por-vencer")
    public List<Map<String, Object>> lotesPorVencer(@RequestParam(defaultValue = "60") int dias) {
        return consultas.lotesPorVencer(dias);
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Map<String, Integer> crearProducto(@Valid @RequestBody CatalogoService.NuevoProducto n) {
        return Map.of("id", servicio.crearProducto(n));
    }

    @PostMapping("/productos/{id}/referencias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Map<String, Integer> agregarReferencia(@PathVariable Integer id, @Valid @RequestBody CatalogoService.NuevaReferencia r) {
        return Map.of("id", servicio.agregarReferencia(id, r));
    }

    @PostMapping("/referencias/{id}/conversiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public void agregarConversion(@PathVariable Integer id, @Valid @RequestBody CatalogoService.Conversion c) {
        servicio.agregarConversion(id, c);
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Categoria crearCategoria(@Valid @RequestBody NuevoNombre n) {
        return servicio.crearCategoria(n.nombre(), n.padreId());
    }

    @PostMapping("/marcas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCTO_EDITAR')")
    public Marca crearMarca(@Valid @RequestBody NuevoNombre n) {
        return servicio.crearMarca(n.nombre());
    }
}
