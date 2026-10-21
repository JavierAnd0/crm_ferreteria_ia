package co.ferreteria.inventario.catalogo;

import co.ferreteria.inventario.comun.NegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF-LEC-002: dígito de control GS1 y secuencia de códigos internos 299. */
class CodigoBarrasTest {

    @ParameterizedTest
    @CsvSource({"299000000001,9", "299000000009,5", "770123456789,7", "400638133393,1"})
    void calculaDigitoDeControl(String doce, int digito) {
        assertThat(CodigoBarras.digitoControl(doce)).isEqualTo(digito);
        assertThat(CodigoBarras.esEan13Valido(doce + digito)).isTrue();
        assertThat(CodigoBarras.esEan13Valido(doce + (digito + 1) % 10)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2990000000019", "2990000000026", "2990000000095", "2990000000101"})
    void losCodigosDemoSonEan13Validos(String codigo) {
        assertThat(CodigoBarras.esEan13Valido(codigo)).isTrue();
    }

    @Test
    void generaElCodigoInternoDeUnaSecuencia() {
        assertThat(CodigoBarras.interno(1)).isEqualTo("2990000000019");
        assertThat(CodigoBarras.interno(10)).isEqualTo("2990000000101");
        assertThat(CodigoBarras.interno(CodigoBarras.SECUENCIA_MAXIMA)).startsWith("299999999999").hasSize(13);
    }

    @Test
    void rechazaSecuenciasFueraDeRango() {
        assertThatThrownBy(() -> CodigoBarras.interno(0)).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> CodigoBarras.interno(CodigoBarras.SECUENCIA_MAXIMA + 1)).isInstanceOf(NegocioException.class);
        assertThat(CodigoBarras.esEan13Valido("12345")).isFalse();
        assertThat(CodigoBarras.esEan13Valido(null)).isFalse();
    }
}
