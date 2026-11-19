package org.academia.correo;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Folder;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class BuzonTest {
    @Test void processesPopRequestRepliesBySmtpAndDeletesOnlyAfterSuccess() throws Exception {
        ejecutarBuzon(200,0,1);
    }

    @Test void failedBackendKeepsRequestInMailboxForRetry() throws Exception {
        ejecutarBuzon(503,1,1);
    }

    @Test void filterSkipsUnrelatedMessages() throws Exception {
        ejecutarBuzon(200,1,1,Map.of("MAIL_MESSAGE_ID_FILTER","<otro-mensaje@test>"));
    }

    @Test void diagnosticModePreservesProcessedMessage() throws Exception {
        ejecutarBuzon(200,1,2,Map.of("MAIL_DELETE_PROCESSED","false"));
    }

    @Test void persistentReceiptAvoidsDuplicateReplyAfterRestart(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        ejecutarBuzon(200,1,2,Map.of("MAIL_DELETE_PROCESSED","false",
                "MAIL_PROCESSED_FILE",dir.resolve("procesados.log").toString()));
    }

    private void ejecutarBuzon(int estado, int pendientes, int mensajesEsperados) throws Exception {
        ejecutarBuzon(estado,pendientes,mensajesEsperados,Map.of());
    }

    private void ejecutarBuzon(int estado, int pendientes, int mensajesEsperados, Map<String,String> opciones) throws Exception {
        GreenMail mail=new GreenMail(new ServerSetup[]{
            new ServerSetup(0,"127.0.0.1",ServerSetup.PROTOCOL_SMTP),
            new ServerSetup(0,"127.0.0.1",ServerSetup.PROTOCOL_POP3)
        });
        mail.start();
        try {
            mail.setUser("academia@local.test","academia","clave-local");
            mail.setUser("director@local.test","director","clave-local");
            GreenMailUtil.sendTextEmail("academia@local.test","director@local.test","HELP","Solicitud",mail.getSmtp().getServerSetup());
            assertTrue(mail.waitForIncomingEmail(5000,1));
            String idOriginal = ServicioEmail.idMensaje(mail.getReceivedMessages()[0]);
            ServicioEmail.Procesador c=(asunto,remitente,id)-> {
                if (estado != 200) throw new java.io.IOException("PostgreSQL no disponible");
                return ComandoEmail.html(Map.of("mensaje", "Solicitud atendida"));
            };
            Map<String,String> config=Map.of("MAIL_POP_HOST","127.0.0.1","MAIL_POP_PORT",String.valueOf(mail.getPop3().getPort()),
                "MAIL_POP_USER","academia","MAIL_POP_PASSWORD","clave-local","MAIL_SMTP_HOST","127.0.0.1",
                "MAIL_SMTP_PORT",String.valueOf(mail.getSmtp().getPort()),"MAIL_FROM","academia@local.test");
            config=new java.util.HashMap<>(config);
            config.putAll(opciones);
            new ServicioEmail(c,config).revisar();
            if (opciones.containsKey("MAIL_PROCESSED_FILE")) {
                new ServicioEmail(c,config).revisar();
                assertEquals(2,mail.getReceivedMessages().length);
                assertEquals(1,java.nio.file.Files.readAllLines(java.nio.file.Path.of(opciones.get("MAIL_PROCESSED_FILE"))).size());
            }
            if (estado==200) assertTrue(mail.waitForIncomingEmail(5000,mensajesEsperados));
            Store store=Session.getInstance(new Properties()).getStore("pop3");
            store.connect("127.0.0.1",mail.getPop3().getPort(),"academia","clave-local");
            try { Folder inbox=store.getFolder("INBOX");inbox.open(Folder.READ_ONLY);assertEquals(pendientes,inbox.getMessageCount());inbox.close(false); }
            finally {store.close();}
            if (estado==200 && !opciones.containsKey("MAIL_MESSAGE_ID_FILTER")) assertTrue(java.util.Arrays.stream(mail.getReceivedMessages()).anyMatch(m->{try{return ServicioEmail.automatico(m);}catch(Exception e){return false;}}));
            if (estado==200 && !opciones.containsKey("MAIL_MESSAGE_ID_FILTER")) {
                var mensajes = mail.getReceivedMessages();
                var respuesta = mensajes[mensajes.length - 1];
                assertTrue(respuesta.getSubject().startsWith("Resultado ayuda - "));
                assertEquals(idOriginal, respuesta.getHeader("In-Reply-To", null));
                assertEquals(idOriginal, respuesta.getHeader("References", null));
                assertTrue(respuesta.isMimeType("text/html"));
                assertArrayEquals(new String[]{"es-BO"},respuesta.getContentLanguage());
            }
        } finally {mail.stop();}
    }
}
