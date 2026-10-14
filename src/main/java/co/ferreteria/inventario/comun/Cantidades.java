package co.ferreteria.inventario.comun;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversión a unidad base y validación de fracciones (RF-PRO-003). */
public final class Cantidades {
    public static final int ESCALA = 4;

    private Cantidades() {
    }

    public static BigDecimal aBase(BigDecimal cantidad, BigDecimal factor) {
        return cantidad.multiply(factor).setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** true si la cantidad en unidad base es múltiplo exacto de la fracción mínima. */
    public static boolean esMultiplo(BigDecimal cantidadBase, BigDecimal fraccionMinima) {
        if (fraccionMinima == null || fraccionMinima.signum() <= 0) return true;
        return cantidadBase.remainder(fraccionMinima).compareTo(BigDecimal.ZERO) == 0;
    }

    /** Precio = costo × (1 + %/100), redondeado hacia arriba al múltiplo indicado (misma regla que fn_calcular_precio). */
    public static BigDecimal precioSugerido(BigDecimal costo, BigDecimal porcentaje, BigDecimal multiplo) {
        BigDecimal bruto = costo.multiply(BigDecimal.ONE.add(porcentaje.movePointLeft(2)));
        return bruto.divide(multiplo, 0, RoundingMode.CEILING).multiply(multiplo).setScale(2, RoundingMode.UNNECESSARY);
    }
}
