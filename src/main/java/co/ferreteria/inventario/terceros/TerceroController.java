package co.ferreteria.inventario.terceros;

import co.ferreteria.inventario.comun.NoEncontradoException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terceros")
public class TerceroController {

    private final TerceroService servicio;
    private final TerceroRepository terceros;

    public TerceroController(TerceroService servicio, TerceroRepository terceros) {
        this.servicio = servicio;
        this.terceros = terceros;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Tercero> buscar(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "false") boolean clientes,
                                @RequestParam(defaultValue = "false") boolean proveedores,
                                @RequestParam(defaultValue = "50") int limite) {
        return terceros.buscar(q.trim(), clientes, proveedores, PageRequest.of(0, Math.min(limite, 200)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Tercero porId(@PathVariable Long id) {
        return terceros.findById(id).orElseThrow(() -> new NoEncontradoException("Tercero", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TERCERO_EDITAR')")
    public Tercero crear(@Valid @RequestBody TerceroService.DatosTercero d) {
        return servicio.crear(d);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TERCERO_EDITAR')")
    public Tercero actualizar(@PathVariable Long id, @Valid @RequestBody TerceroService.DatosTercero d) {
        return servicio.actualizar(id, d);
    }
}
