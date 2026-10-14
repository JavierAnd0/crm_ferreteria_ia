package co.ferreteria.inventario.terceros;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DigitoVerificacionTest {

    @ParameterizedTest
    @CsvSource({"900123456,8", "901987654,0", "860002964,4", "800197268,4"})
    void calculaDigitoDian(String nit, int dv) {
        assertThat(DigitoVerificacion.calcular(nit)).isEqualTo(dv);
        assertThat(DigitoVerificacion.esValido(nit, dv)).isTrue();
        assertThat(DigitoVerificacion.esValido(nit, (dv + 1) % 10)).isFalse();
    }
}
