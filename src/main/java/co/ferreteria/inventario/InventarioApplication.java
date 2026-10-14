package co.ferreteria.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Inventario de ferretería: compras, ventas, existencias por estado, precios, cierres y auditoría.
 * La transacción se abre antes que el aspecto de auditoría (orden 0) para que el contexto
 * de usuario quede fijado dentro de la misma transacción.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableTransactionManagement(order = 0)
public class InventarioApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventarioApplication.class, args);
    }
}
