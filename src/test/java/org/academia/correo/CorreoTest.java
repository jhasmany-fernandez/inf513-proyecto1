package org.academia.correo;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.academia.dominio.MotorAcademico;
import org.academia.dominio.ErrorComando;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CorreoTest {
    @Test void helpUsesOrderedColumnsAndSpanishLanguage() throws Exception {
        String html=ComandoEmail.html(new MotorAcademico(null,Map.of("rol","director")).ejecutar("ayuda"));
        assertTrue(html.contains("<html lang=\"es-BO\">"));
        assertTrue(html.contains("content=\"es-BO\""));
        assertTrue(html.indexOf(">Caso</th>")<html.indexOf(">Comando</th>"));
        assertTrue(html.indexOf(">Comando</th>")<html.indexOf(">Par\u00e1metros y ejemplos</th>"));
        assertTrue(html.indexOf(">CU3</td>")<html.indexOf(">CU6</td>"));
        assertTrue(html.lastIndexOf(">CU6</td>")<html.indexOf(">CU8</td>"));
        assertTrue(html.contains("reporte general [2026-10-01; 2026-10-31]"));
        assertFalse(html.contains("O [desde; hasta]."));
    }
    @Test void recordUsesFormalFieldTable() {
        String html = ComandoEmail.html(Map.of("mensaje", "Registro creado", "datos",
                java.util.List.of(Map.of("id", 2, "estudiante_id", 2, "requisitos", "<script>malicioso</script>"))));
        assertTrue(html.contains(">Campo</th>"));
        assertTrue(html.contains(">Detalle</th>"));
        assertTrue(html.contains("ID del estudiante"));
        assertFalse(html.contains("<ol>"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
    @Test void listUsesColumnsAndEmptyResultIsExplicit() {
        String html = ComandoEmail.html(Map.of("datos", java.util.List.of(Map.of("nombre", "Ana"), Map.of("nombre", "Luis", "id", 3))));
        assertTrue(html.contains("scope=\"col\""));
        assertTrue(html.contains("Ana"));
        assertTrue(html.contains("Luis"));
        assertTrue(html.contains("&mdash;"));
        assertTrue(ComandoEmail.html(Map.of("datos", java.util.List.of())).contains("No hay registros"));
        assertTrue(ComandoEmail.html(Map.of("error", "No autorizado")).contains("data-resultado=\"error\""));
    }
    @Test void responsePresentationAndSubjectDoNotExposeCommandParameters() {
        String html = ComandoEmail.html(Map.of("mensaje", "Prueba", "error", "<img src=x>"));
        assertTrue(html.contains("name=\"viewport\""));
        assertTrue(html.contains("<footer>"));
        assertFalse(html.contains("<img src=x>"));
        assertEquals("usuario agregar", ServicioEmail.nombreComando("USUARIO AGREGAR[Nombre,correo_electronico,clave,rol,1]"));
        assertEquals("ayuda", ServicioEmail.nombreComando(" help "));
        assertEquals("solicitud", ServicioEmail.nombreComando("UPDATECAMPANAS[4,datos]"));
        assertEquals("solicitud", ServicioEmail.nombreComando("<invalido>"));
    }
    @Test void csvSupportsQuotedCommas() throws Exception {
        assertEquals(java.util.List.of("Curso, nivel 1", "300", ""), MotorAcademico.parametros("\"Curso, nivel 1\",300,"));
        assertThrows(ErrorComando.class, () -> MotorAcademico.parametros("\"sin cerrar"));
    }
    @Test void responseEscapesHtml() {
        assertFalse(ComandoEmail.html(Map.of("nombre", "<script>alert(1)</script>")).contains("<script>"));
    }
    @Test void responseUsesAdministrativeProjectName() {
        String html=ComandoEmail.html(Map.of("mensaje", "Solicitud atendida"));
        assertTrue(html.contains(IdentidadProyecto.NOMBRE));
        assertTrue(html.contains("Grupo 22SC"));
        assertFalse(html.contains("Grupo 16-B"));
    }
    @Test void databaseFailureKeepsRequestRetryable() {
        ComandoEmail c = new ComandoEmail(() -> { throw new java.sql.SQLException("offline"); });
        assertThrows(IOException.class, () -> c.ejecutar("HELP", "director@academia.test", "<uno>"));
    }
    @Test void mimeLibraryDecodesSubjectsAndIgnoresAutomaticReplies() throws Exception {
        String raw="From: Director <director@academia.test>\r\nSubject: =?UTF-8?B?SU5HTMOJUw==?=\r\nMessage-ID: <academia-1>\r\nAuto-Submitted: auto-replied\r\n\r\nTexto";
        MimeMessage m=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertEquals("INGLÉS",m.getSubject()); assertEquals("<academia-1>",ServicioEmail.idMensaje(m)); assertTrue(ServicioEmail.automatico(m));
    }
}
