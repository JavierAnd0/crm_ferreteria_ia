package co.ferreteria.inventario.catalogo;

import co.ferreteria.inventario.comun.NegocioException;

/**
 * RF-LEC-002: códigos EAN-13 internos para referencias sin código del fabricante (o con un código repetido, RN-01).
 * Usan el prefijo 299, que GS1 reserva para circulación restringida dentro de la tienda, más una secuencia de 9
 * dígitos y el dígito de control: 299 + 000000001 + 9 = 2990000000019.
 */
public final class CodigoBarras {
    public static final String PREFIJO_INTERNO = "299";
    public static final long SECUENCIA_MAXIMA = 999_999_999L;

    private CodigoBarras() {
    }

    /** Dígito de control GS1 de los 12 primeros dígitos: pesos 1 y 3 alternados desde la izquierda. */
    public static int digitoControl(String doceDigitos) {
        if (doceDigitos == null || !doceDigitos.matches("\\d{12}")) {
            throw new IllegalArgumentException("Se esperaban 12 dígitos: " + doceDigitos);
        }
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            suma += Character.digit(doceDigitos.charAt(i), 10) * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - suma % 10) % 10;
    }

    public static boolean esEan13Valido(String codigo) {
        return codigo != null && codigo.matches("\\d{13}") && digitoControl(codigo.substring(0, 12)) == Character.digit(codigo.charAt(12), 10);
    }

    /** Código interno de la secuencia dada (1 a 999.999.999). */
    public static String interno(long secuencia) {
        if (secuencia < 1 || secuencia > SECUENCIA_MAXIMA) {
            throw new NegocioException("Se agotaron los códigos de barras internos (prefijo " + PREFIJO_INTERNO + ").");
        }
        String doce = PREFIJO_INTERNO + String.format("%09d", secuencia);
        return doce + digitoControl(doce);
    }
}
