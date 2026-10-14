package co.ferreteria.inventario.comun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CantidadesTest {

    @Test
    void convierteAUnidadBase() {
        // 2 bultos de 50 kg = 100 kg; 3 libras de 0,5 kg = 1,5 kg
        assertThat(Cantidades.aBase(new BigDecimal("2"), new BigDecimal("50"))).isEqualByComparingTo("100");
        assertThat(Cantidades.aBase(new BigDecimal("3"), new BigDecimal("0.5"))).isEqualByComparingTo("1.5");
    }

    @ParameterizedTest
    @CsvSource({"1.5,0.5,true", "0.3,0.5,false", "2.75,0.25,true", "0.1,0.25,false", "7,1,true", "7.5,1,false"})
    void validaMultiploDeFraccionMinima(String cantidad, String fraccion, boolean esperado) {
        assertThat(Cantidades.esMultiplo(new BigDecimal(cantidad), new BigDecimal(fraccion))).isEqualTo(esperado);
    }

    @Test
    void sinFraccionMinimaTodoEsValido() {
        assertThat(Cantidades.esMultiplo(new BigDecimal("0.333"), null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"62000,30,80600", "14500,35,19600", "11800,40,16550", "100,0,100"})
    void precioRedondeaHaciaArribaAMultiploDe50(String costo, String pct, String esperado) {
        assertThat(Cantidades.precioSugerido(new BigDecimal(costo), new BigDecimal(pct), new BigDecimal("50")))
                .isEqualByComparingTo(esperado);
    }
}
