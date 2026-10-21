package co.ferreteria.inventario.flujos;

import co.ferreteria.inventario.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usuarios y permisos: autenticación con JWT y autorización por permiso. */
class SeguridadTest extends PruebaIntegracion {

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/referencias")).andExpect(status().isUnauthorized());
    }

    @Test
    void contrasenaErradaNoEntrega() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "admin", "contrasena", "otra-cosa"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contrasenaErradaExplicaElMotivo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "admin", "contrasena", "otra-cosa"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void unTokenViejoNoImpideIngresar() throws Exception {
        // El navegador guarda el token de la sesión anterior; vencido o firmado con otra clave no debe bloquear el ingreso.
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + TOKEN_INVALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "vendedor1", "contrasena", CLAVE_DEMO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void tokenInvalidoRespondeConMensaje() throws Exception {
        mvc.perform(get("/api/referencias").header("Authorization", "Bearer " + TOKEN_INVALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La sesión terminó o no es válida. Ingrese de nuevo."));
        mvc.perform(get("/api/referencias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La sesión terminó o no es válida. Ingrese de nuevo."));
    }

    private static final String TOKEN_INVALIDO = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ2ZW5kZWRvcjEifQ.firma-de-otra-clave";

    @Test
    void elTokenTraeLosPermisosDelRol() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "vendedor1", "contrasena", CLAVE_DEMO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.hasItem("VENTA_EMITIR")))
                .andExpect(jsonPath("$.permisos").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("COMPRA_REGISTRAR"))));
    }

    @Test
    void vendedorNoPuedeComprarNiVerCostosNiAuditoria() throws Exception {
        enviar("vendedor1", "/api/compras", Map.of("proveedorId", 1, "numeroFacturaProveedor", "X", "fechaFacturaProveedor", "2026-10-01",
                "lineas", java.util.List.of(Map.of("referenciaId", 1, "cantidad", 1, "costoUnitario", 1)))).andExpect(status().isForbidden());
        consultar("vendedor1", "/api/reportes/margen").andExpect(status().isForbidden());
        consultar("vendedor1", "/api/auditoria").andExpect(status().isForbidden());
        consultar("vendedor1", "/api/usuarios").andExpect(status().isForbidden());
    }

    @Test
    void sinPermisoDeCostosNoSeVeElCosto() throws Exception {
        consultar("vendedor1", "/api/referencias?q=taladro").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costoPromedio").doesNotExist());
        consultar("gerente1", "/api/referencias?q=taladro").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].costoPromedio").exists());
    }

    @Test
    void crearUsuarioQuedaAuditadoConQuienLoCreoYSinElHashDeLaClave() throws Exception {
        enviar("admin", "/api/usuarios", Map.of("login", "cajero9", "nombre", "Cajero de prueba",
                "contrasena", "Clave-Segura-1", "roles", java.util.List.of("VENTAS"))).andExpect(status().isCreated());
        var fila = jdbc.sql("""
                        SELECT u.login AS autor, a.despues::text AS despues FROM auditoria a JOIN usuario u ON u.id = a.usuario_id
                         WHERE a.tabla = 'usuario' AND a.accion = 'INSERT' AND a.despues->>'login' = 'cajero9'""")
                .query().singleRow();
        org.assertj.core.api.Assertions.assertThat(fila.get("autor")).isEqualTo("admin");
        org.assertj.core.api.Assertions.assertThat((String) fila.get("despues")).doesNotContain("hash_contrasena");
    }
}
