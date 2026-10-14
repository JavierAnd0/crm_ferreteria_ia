package co.ferreteria.inventario.migracion;

import co.ferreteria.inventario.catalogo.CatalogoService;
import co.ferreteria.inventario.comun.Cantidades;
import co.ferreteria.inventario.comun.ContextoAuditoria;
import co.ferreteria.inventario.comun.Fechas;
import co.ferreteria.inventario.comun.NegocioException;
import co.ferreteria.inventario.comun.NoEncontradoException;
import co.ferreteria.inventario.inventario.InfoReferencias;
import co.ferreteria.inventario.inventario.Kardex;
import co.ferreteria.inventario.inventario.Movimiento;
import co.ferreteria.inventario.seguridad.UsuarioActual;
import co.ferreteria.inventario.terceros.TerceroService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * RF-MIG-001. Paso 1 (recibir): guarda cada fila original en importacion_fila y la valida.
 * Paso 2 (cargar): aplica las filas válidas en orden TERCEROS → PRODUCTOS → SALDOS; cada fila
 * en su propia transacción para que un error no detenga el resto. El mismo archivo no se carga dos veces.
 */
@Service
public class MigracionService {

    public record Resumen(long id, String estado, int total, int validas, int errores, int cargadas) {}

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final TerceroService terceros;
    private final CatalogoService catalogo;
    private final InfoReferencias referencias;
    private final Kardex kardex;

    public MigracionService(JdbcClient jdbc, ObjectMapper json, TerceroService terceros, CatalogoService catalogo,
                            InfoReferencias referencias, Kardex kardex) {
        this.jdbc = jdbc;
        this.json = json;
        this.terceros = terceros;
        this.catalogo = catalogo;
        this.referencias = referencias;
        this.kardex = kardex;
    }

    @Transactional
    public Resumen recibir(String nombreArchivo, byte[] contenido, LocalDate fechaCorte) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        if (jdbc.sql("SELECT count(*) FROM importacion WHERE archivo_sha256 = :s").param("s", sha).query(Long.class).single() > 0)
            throw new NegocioException("Este archivo ya fue importado");
        List<HojasExcel.Fila> filas = HojasExcel.leer(new java.io.ByteArrayInputStream(contenido));
        long id = jdbc.sql("""
                        INSERT INTO importacion (archivo_nombre, archivo_sha256, fecha_corte, usuario_id, filas_total)
                        VALUES (:n, :s, :f, :u, :t) RETURNING id""")
                .param("n", nombreArchivo).param("s", sha).param("f", fechaCorte).param("u", UsuarioActual.id())
                .param("t", filas.size()).query(Long.class).single();
        Set<String> barrasEnArchivo = new HashSet<>();
        filas.stream().filter(f -> f.hoja().equals("PRODUCTOS")).forEach(f -> barrasEnArchivo.add(f.datos().get("codigo_barras")));
        Set<String> vistos = new HashSet<>();
        for (HojasExcel.Fila f : filas) {
            List<String> errores = validar(f, barrasEnArchivo, vistos);
            jdbc.sql("""
                            INSERT INTO importacion_fila (importacion_id, hoja, numero_fila, datos, estado, errores)
                            VALUES (:i, :h, :n, CAST(:d AS jsonb), :e, CAST(:err AS jsonb))""")
                    .param("i", id).param("h", f.hoja()).param("n", f.numero()).param("d", json.writeValueAsString(f.datos()))
                    .param("e", errores.isEmpty() ? "VALIDA" : "ERROR")
                    .param("err", errores.isEmpty() ? null : json.writeValueAsString(errores)).update();
        }
        jdbc.sql("""
                        UPDATE importacion SET estado = 'VALIDADA',
                               filas_validas = (SELECT count(*) FROM importacion_fila WHERE importacion_id = :i AND estado = 'VALIDA'),
                               filas_error = (SELECT count(*) FROM importacion_fila WHERE importacion_id = :i AND estado = 'ERROR')
                         WHERE id = :i""").param("i", id).update();
        return resumen(id);
    }

    public Resumen resumen(long id) {
        return jdbc.sql("""
                        SELECT i.id, i.estado, i.filas_total, i.filas_validas, i.filas_error,
                               (SELECT count(*) FROM importacion_fila f WHERE f.importacion_id = i.id AND f.estado = 'CARGADA')
                          FROM importacion i WHERE i.id = :id""").param("id", id)
                .query((rs, n) -> new Resumen(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)))
                .optional().orElseThrow(() -> new NoEncontradoException("Importación", id));
    }

    /** Ids de filas válidas en el orden de carga. */
    @Transactional(readOnly = true)
    public List<Long> filasPorCargar(long importacionId) {
        String estado = jdbc.sql("SELECT estado FROM importacion WHERE id = :id").param("id", importacionId)
                .query(String.class).optional().orElseThrow(() -> new NoEncontradoException("Importación", importacionId));
        if (!"VALIDADA".equals(estado)) throw new NegocioException("La importación está en estado " + estado);
        return jdbc.sql("""
                        SELECT id FROM importacion_fila WHERE importacion_id = :i AND estado = 'VALIDA'
                         ORDER BY CASE hoja WHEN 'TERCEROS' THEN 1 WHEN 'PRODUCTOS' THEN 2 ELSE 3 END, numero_fila""")
                .param("i", importacionId).query(Long.class).list();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cargarFila(long filaId) throws Exception {
        record F(long importacionId, String hoja, String datos, LocalDate fechaCorte) {}
        F f = jdbc.sql("""
                        SELECT f.importacion_id, f.hoja, f.datos::text, i.fecha_corte
                          FROM importacion_fila f JOIN importacion i ON i.id = f.importacion_id WHERE f.id = :id""")
                .param("id", filaId).query(F.class).single();
        Map<String, String> d = json.readValue(f.datos(), new TypeReference<>() {});
        Object[] destino = ContextoAuditoria.conOrigen("MIGRACION", () -> switch (f.hoja()) {
            case "TERCEROS" -> new Object[]{"TERCERO", terceros.crear(tercero(d)).getId()};
            case "PRODUCTOS" -> new Object[]{"REFERENCIA", producto(d).longValue()};
            default -> new Object[]{"MOVIMIENTO", saldo(d, f.fechaCorte(), f.importacionId())};
        });
        jdbc.sql("UPDATE importacion_fila SET estado = 'CARGADA', entidad_destino = :e, entidad_id = :id WHERE id = :f")
                .param("e", destino[0]).param("id", destino[1]).param("f", filaId).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarError(long filaId, String error) throws Exception {
        jdbc.sql("UPDATE importacion_fila SET estado = 'ERROR', errores = CAST(:e AS jsonb) WHERE id = :id")
                .param("e", json.writeValueAsString(List.of(error))).param("id", filaId).update();
    }

    @Transactional
    public Resumen finalizar(long importacionId) {
        jdbc.sql("""
                        UPDATE importacion SET estado = 'CARGADA',
                               filas_error = (SELECT count(*) FROM importacion_fila WHERE importacion_id = :i AND estado = 'ERROR')
                         WHERE id = :i""").param("i", importacionId).update();
        jdbc.sql("""
                        SELECT fn_actualizar_precio(r.id, 'MIGRACION', :u) FROM referencia r
                         WHERE r.id IN (SELECT entidad_id FROM importacion_fila WHERE importacion_id = :i AND entidad_destino = 'REFERENCIA')
                            OR r.id IN (SELECT m.referencia_id FROM movimiento m WHERE m.documento_tipo = 'MIGRACION' AND m.documento_id = :i)""")
                .param("u", UsuarioActual.id()).param("i", importacionId).query().listOfRows();
        return resumen(importacionId);
    }

    // ---------------------------------------------------------------- validación por fila
    private List<String> validar(HojasExcel.Fila f, Set<String> barrasEnArchivo, Set<String> vistos) {
        List<String> e = new ArrayList<>();
        Map<String, String> d = f.datos();
        switch (f.hoja()) {
            case "TERCEROS" -> {
                try {
                    TerceroService.validarPublico(tercero(d));
                } catch (RuntimeException ex) {
                    e.add(ex.getMessage());
                }
                if (!vistos.add("T:" + d.get("tipo_identificacion") + d.get("numero"))) e.add("Identificación repetida en el archivo");
            }
            case "PRODUCTOS" -> {
                for (String c : List.of("codigo_interno", "nombre", "categoria", "marca", "presentacion", "codigo_barras", "unidad_base"))
                    if (vacio(d.get(c))) e.add("Falta " + c);
                decimal(d, "porcentaje_incremento", e, false);
                decimal(d, "fraccion_minima", e, false);
                if (!vistos.add("B:" + d.get("codigo_barras"))) e.add("Código de barras repetido en el archivo");
                if (!vacio(d.get("codigo_barras")) && jdbc.sql("SELECT count(*) FROM referencia WHERE codigo_barras = :c")
                        .param("c", d.get("codigo_barras")).query(Long.class).single() > 0) e.add("El código de barras ya existe");
            }
            default -> {
                if (vacio(d.get("codigo_barras"))) e.add("Falta codigo_barras");
                else if (!barrasEnArchivo.contains(d.get("codigo_barras")) && jdbc.sql("SELECT count(*) FROM referencia WHERE codigo_barras = :c")
                        .param("c", d.get("codigo_barras")).query(Long.class).single() == 0) e.add("Código de barras inexistente");
                BigDecimal cant = decimal(d, "cantidad", e, true);
                if (cant != null && cant.signum() <= 0) e.add("La cantidad debe ser mayor que cero");
                BigDecimal costo = decimal(d, "costo_unitario", e, true);
                if (costo != null && costo.signum() < 0) e.add("El costo no puede ser negativo");
                if (!vacio(d.get("fecha_vencimiento"))) {
                    try {
                        LocalDate.parse(d.get("fecha_vencimiento"));
                    } catch (Exception ex) {
                        e.add("fecha_vencimiento debe tener formato AAAA-MM-DD");
                    }
                }
            }
        }
        return e;
    }

    // ---------------------------------------------------------------- carga por hoja
    private static TerceroService.DatosTercero tercero(Map<String, String> d) {
        return new TerceroService.DatosTercero(mayus(d.get("tipo_persona")), mayus(d.get("tipo_identificacion")), d.get("numero"),
                vacio(d.get("dv")) ? null : Short.valueOf(d.get("dv")), d.get("nombres"), d.get("apellidos"), d.get("razon_social"),
                d.get("direccion"), d.get("municipio"), d.get("telefono"), vacio(d.get("correo")) ? null : d.get("correo"),
                si(d.get("es_cliente")), si(d.get("es_proveedor")));
    }

    private Integer producto(Map<String, String> d) {
        int categoria = jdbc.sql("""
                        WITH n AS (INSERT INTO categoria (nombre) SELECT :c WHERE NOT EXISTS (SELECT 1 FROM categoria WHERE nombre = :c AND padre_id IS NULL) RETURNING id)
                        SELECT id FROM n UNION ALL SELECT id FROM categoria WHERE nombre = :c AND padre_id IS NULL LIMIT 1""")
                .param("c", d.get("categoria").trim()).query(Integer.class).single();
        int marca = jdbc.sql("""
                        WITH n AS (INSERT INTO marca (nombre) SELECT :m WHERE NOT EXISTS (SELECT 1 FROM marca WHERE lower(nombre) = lower(:m)) RETURNING id)
                        SELECT id FROM n UNION ALL SELECT id FROM marca WHERE lower(nombre) = lower(:m) LIMIT 1""")
                .param("m", d.get("marca").trim()).query(Integer.class).single();
        List<CatalogoService.Conversion> conversiones = vacio(d.get("unidad_alterna")) ? List.of()
                : List.of(new CatalogoService.Conversion(d.get("unidad_alterna"), new BigDecimal(d.get("factor_alterna")), true, true));
        var ref = new CatalogoService.NuevaReferencia(marca, d.get("presentacion"), d.get("codigo_barras"), d.get("unidad_base"),
                si(d.get("permite_fraccion")), vacio(d.get("fraccion_minima")) ? BigDecimal.ONE : new BigDecimal(d.get("fraccion_minima")),
                vacio(d.get("meses_garantia")) ? null : Short.valueOf(d.get("meses_garantia")),
                vacio(d.get("tarifa_iva")) ? "IVA19" : d.get("tarifa_iva"),
                vacio(d.get("porcentaje_incremento")) ? BigDecimal.ZERO : new BigDecimal(d.get("porcentaje_incremento")),
                vacio(d.get("stock_minimo")) ? null : new BigDecimal(d.get("stock_minimo")), conversiones);
        Optional<Integer> producto = jdbc.sql("SELECT id FROM producto WHERE codigo_interno = :c").param("c", d.get("codigo_interno"))
                .query(Integer.class).optional();
        if (producto.isPresent()) return catalogo.agregarReferencia(producto.get(), ref);
        catalogo.crearProducto(new CatalogoService.NuevoProducto(d.get("codigo_interno"), d.get("nombre"), null, categoria,
                si(d.get("controla_vencimiento")), List.of(ref)));
        return jdbc.sql("SELECT id FROM referencia WHERE codigo_barras = :c").param("c", d.get("codigo_barras")).query(Integer.class).single();
    }

    private long saldo(Map<String, String> d, LocalDate fechaCorte, long importacionId) {
        int ref = jdbc.sql("SELECT id FROM referencia WHERE codigo_barras = :c").param("c", d.get("codigo_barras"))
                .query(Integer.class).optional().orElseThrow(() -> new NegocioException("Código de barras inexistente"));
        InfoReferencias.Info info = referencias.info(ref);
        Long lote = info.controlaVencimiento()
                ? referencias.loteParaCompra(ref, d.get("lote"), vacio(d.get("fecha_vencimiento")) ? null : LocalDate.parse(d.get("fecha_vencimiento")), null)
                : null;
        OffsetDateTime fecha = fechaCorte.atTime(LocalTime.of(23, 0)).atZone(Fechas.ZONA).toOffsetDateTime();
        BigDecimal cantidad = new BigDecimal(d.get("cantidad")).setScale(Cantidades.ESCALA);
        return kardex.registrar(new Movimiento(fecha, "SALDO_INICIAL", ref, lote, null, "DISPONIBLE", cantidad,
                new BigDecimal(d.get("costo_unitario")), "MIGRACION", importacionId, null, null, null), null).id();
    }

    private static BigDecimal decimal(Map<String, String> d, String campo, List<String> errores, boolean obligatorio) {
        String v = d.get(campo);
        if (vacio(v)) {
            if (obligatorio) errores.add("Falta " + campo);
            return null;
        }
        try {
            return new BigDecimal(v.replace(",", "."));
        } catch (NumberFormatException ex) {
            errores.add(campo + " no es un número: " + v);
            return null;
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static boolean si(String s) {
        return s != null && Set.of("S", "SI", "SÍ", "TRUE", "1", "X").contains(s.trim().toUpperCase());
    }

    private static String mayus(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }
}
