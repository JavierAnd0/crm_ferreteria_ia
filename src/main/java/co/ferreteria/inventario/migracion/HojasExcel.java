package co.ferreteria.inventario.migracion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Lectura y plantilla del libro de migración (RF-MIG-001). */
public final class HojasExcel {

    public static final Map<String, List<String>> COLUMNAS = new LinkedHashMap<>();

    static {
        COLUMNAS.put("TERCEROS", List.of("tipo_persona", "tipo_identificacion", "numero", "dv", "nombres", "apellidos",
                "razon_social", "direccion", "municipio", "telefono", "correo", "es_cliente", "es_proveedor"));
        COLUMNAS.put("PRODUCTOS", List.of("codigo_interno", "nombre", "categoria", "controla_vencimiento", "marca", "presentacion",
                "codigo_barras", "unidad_base", "permite_fraccion", "fraccion_minima", "meses_garantia", "tarifa_iva",
                "porcentaje_incremento", "stock_minimo", "unidad_alterna", "factor_alterna"));
        COLUMNAS.put("SALDOS", List.of("codigo_barras", "lote", "fecha_vencimiento", "cantidad", "costo_unitario"));
    }

    private static final List<List<String>> EJEMPLOS = List.of(
            List.of("JURIDICA", "NIT", "900123456", "8", "", "", "Proveedor Ejemplo S.A.S.", "Calle 1 # 2-3", "Bogotá",
                    "6010000000", "proveedor@ejemplo.co", "N", "S"),
            List.of("ELE-CAB-12", "Cable THHN 12 AWG", "Material eléctrico", "N", "Centelsa", "Rollo 100 m", "2990000000088",
                    "M", "S", "0.5", "", "IVA19", "35", "100", "ROLLO", "100"),
            List.of("2990000000088", "", "", "200", "1800"));

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private HojasExcel() {
    }

    public record Fila(String hoja, int numero, Map<String, String> datos) {}

    public static List<Fila> leer(InputStream entrada) throws IOException {
        List<Fila> filas = new ArrayList<>();
        DataFormatter formato = new DataFormatter(Locale.ROOT);
        try (Workbook libro = WorkbookFactory.create(entrada)) {
            for (String nombreHoja : COLUMNAS.keySet()) {
                Sheet hoja = libro.getSheet(nombreHoja);
                if (hoja == null) continue;
                Row cabecera = hoja.getRow(0);
                if (cabecera == null) continue;
                List<String> columnas = new ArrayList<>();
                cabecera.forEach(c -> columnas.add(formato.formatCellValue(c).trim().toLowerCase()));
                for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                    Row fila = hoja.getRow(i);
                    if (fila == null) continue;
                    Map<String, String> datos = new LinkedHashMap<>();
                    boolean vacia = true;
                    for (int c = 0; c < columnas.size(); c++) {
                        Cell celda = fila.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String valor = "";
                        if (celda != null) {
                            valor = celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)
                                    ? celda.getLocalDateTimeCellValue().toLocalDate().format(ISO)
                                    : formato.formatCellValue(celda).trim();
                        }
                        if (!valor.isEmpty()) vacia = false;
                        datos.put(columnas.get(c), valor);
                    }
                    if (!vacia) filas.add(new Fila(nombreHoja, i + 1, datos));
                }
            }
        }
        return filas;
    }

    public static byte[] plantilla() throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            int i = 0;
            for (var e : COLUMNAS.entrySet()) {
                Sheet hoja = libro.createSheet(e.getKey());
                Row cab = hoja.createRow(0);
                Row ej = hoja.createRow(1);
                for (int c = 0; c < e.getValue().size(); c++) {
                    cab.createCell(c).setCellValue(e.getValue().get(c));
                    ej.createCell(c).setCellValue(EJEMPLOS.get(i).get(c));
                    hoja.setColumnWidth(c, 18 * 256);
                }
                i++;
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }
}
