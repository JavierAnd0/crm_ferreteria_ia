package co.ferreteria.inventario.comun;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlBaseDatosTest {

    @Test
    void traduceLaCadenaDeNeonAJdbc() {
        var propiedades = UrlBaseDatos.traducir(
                "postgresql://neondb_owner:Ab%2Fc+d@ep-soft-math.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

        assertThat(propiedades).containsEntry("spring.datasource.url",
                        "jdbc:postgresql://ep-soft-math.us-east-1.aws.neon.tech/neondb?sslmode=require&currentSchema=ferreteria&stringtype=unspecified")
                .containsEntry("spring.datasource.username", "neondb_owner")
                .containsEntry("spring.datasource.password", "Ab/c+d");
    }

    @Test
    void conservaElPuertoYAceptaPostgres() {
        assertThat(UrlBaseDatos.traducir("postgres://u:p@db:6543/ferreteria"))
                .containsEntry("spring.datasource.url",
                        "jdbc:postgresql://db:6543/ferreteria?currentSchema=ferreteria&stringtype=unspecified");
    }

    @Test
    void rechazaOtrosEsquemas() {
        assertThatThrownBy(() -> UrlBaseDatos.traducir("mysql://u:p@db/x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dbUrlTienePrioridad() {
        var entorno = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@neon/neondb")
                .withProperty("DB_URL", "jdbc:postgresql://local/ferreteria");
        new UrlBaseDatos().postProcessEnvironment(entorno, new SpringApplication());
        assertThat(entorno.getProperty("spring.datasource.username")).isNull();

        var soloNeon = new MockEnvironment().withProperty("DATABASE_URL", "postgresql://u:p@neon/neondb");
        new UrlBaseDatos().postProcessEnvironment(soloNeon, new SpringApplication());
        assertThat(soloNeon.getProperty("spring.datasource.username")).isEqualTo("u");
    }
}
