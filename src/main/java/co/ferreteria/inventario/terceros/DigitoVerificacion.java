package co.ferreteria.inventario.terceros;

/** Dígito de verificación del NIT según el algoritmo de la DIAN (módulo 11 con pesos primos). */
public final class DigitoVerificacion {
    private static final int[] PESOS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    private DigitoVerificacion() {
    }

    public static int calcular(String nit) {
        if (nit == null || !nit.matches("\\d{1,15}")) throw new IllegalArgumentException("NIT inválido: " + nit);
        int suma = 0;
        for (int i = 0; i < nit.length(); i++) {
            suma += Character.digit(nit.charAt(nit.length() - 1 - i), 10) * PESOS[i];
        }
        int residuo = suma % 11;
        return residuo >= 2 ? 11 - residuo : residuo;
    }

    public static boolean esValido(String nit, Integer dv) {
        return dv != null && nit != null && nit.matches("\\d{1,15}") && calcular(nit) == dv;
    }
}
