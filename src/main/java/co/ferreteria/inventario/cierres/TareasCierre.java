package co.ferreteria.inventario.cierres;

import co.ferreteria.inventario.comun.Fechas;
import co.ferreteria.inventario.inventario.TareasInventario.ComoSistema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cierre mensual programado (día 1, 02:00 hora de Colombia) y mantenimiento de particiones. */
@Component
public class TareasCierre {

    private static final Logger log = LoggerFactory.getLogger(TareasCierre.class);
    private final CierreService cierres;

    public TareasCierre(CierreService cierres) {
        this.cierres = cierres;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        log.info("Particiones nuevas creadas: {}", cierres.mantenerParticiones());
    }

    @Scheduled(cron = "${app.cierre.cron}", zone = "America/Bogota")
    public void cierreProgramado() {
        var mesAnterior = Fechas.hoy().minusMonths(1);
        int periodo = mesAnterior.getYear() * 100 + mesAnterior.getMonthValue();
        ComoSistema.ejecutar(() -> {
            cierres.mantenerParticiones();
            try {
                log.info("Cierre programado {}: {} saldos", periodo, cierres.cerrar(periodo));
            } catch (RuntimeException e) {
                // Se aplaza (p. ej. operaciones sin internet pendientes); queda en el log y se puede ejecutar a mano.
                log.error("Cierre programado {} aplazado: {}", periodo, e.getMessage());
            }
        });
    }
}
