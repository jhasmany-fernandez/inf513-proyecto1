package org.academia.pagos;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagoFacilCheckoutTest {
    private final PagoFacilCheckout checkout = new PagoFacilCheckout("comercio-prueba", "servicio-prueba", "", "");

    @Test void instructorYSecretariaNoPuedenSimularPagos() {
        for (String rol : new String[]{"instructor", "secretaria"}) {
            var motor = new org.academia.dominio.MotorAcademico(null, java.util.Map.of("rol", rol));
            var error = assertThrows(org.academia.dominio.ErrorComando.class,
                    () -> motor.ejecutar("pago confirmar [ACA-TEST]"));
            assertTrue(error.getMessage().contains("Solo el director"));
        }
    }

    @Test void rechazaMontosInvalidos() {
        for (String monto : new String[]{"0", "-1", "0.001"}) {
            assertThrows(IllegalArgumentException.class, () -> checkout.datos(
                    "cliente@example.test", "70000000", "ACA-TEST", new BigDecimal(monto), "Inscripción"));
        }
    }

    @Test void conservaMonedaMontoYDescripcionEnCheckout() {
        String datos = new String(Base64.getDecoder().decode(checkout.datos(
                "cliente@example.test", "70000000", "ACA-TEST", new BigDecimal("0.01"), "Inscripción")), StandardCharsets.UTF_8);
        String[] campos = datos.split("\\|", -1);
        assertEquals(10, campos.length);
        assertEquals("ACA-TEST", campos[3]);
        assertEquals("0.01", campos[4]);
        assertEquals("2", campos[5]);
        assertTrue(campos[8].contains("Inscripción"));
        assertEquals("11", campos[9]);
    }
}
