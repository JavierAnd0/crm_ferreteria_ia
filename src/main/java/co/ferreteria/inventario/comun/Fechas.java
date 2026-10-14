package co.ferreteria.inventario.comun;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class Fechas {
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private Fechas() {
    }

    public static OffsetDateTime ahora() {
        return OffsetDateTime.now(ZONA);
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** Período AAAAMM de una fecha en hora de Colombia. */
    public static int periodo(OffsetDateTime fecha) {
        var local = fecha.atZoneSameInstant(ZONA);
        return local.getYear() * 100 + local.getMonthValue();
    }
}
