package org.academia.correo;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Map;

public final class ServicioEmail {
    @FunctionalInterface interface Procesador { String ejecutar(String asunto, String remitente, String id) throws Exception; }
    private final Procesador comandos;
    private final Map<String, String> configuracion;

    public ServicioEmail() {
        this(new ComandoEmail()::ejecutar, System.getenv());
    }

    ServicioEmail(Procesador comandos, Map<String, String> configuracion) {
        this.comandos = comandos;
        this.configuracion = Map.copyOf(configuracion);
    }

    private String valor(String nombre, String defecto) {
        String v = configuracion.get(nombre);
        return v == null || v.isBlank() ? defecto : v;
    }

    private String requerido(String nombre) {
        String v = valor(nombre, "");
        if (v.isBlank()) throw new IllegalStateException("Configure " + nombre);
        return v;
    }

    public void revisar() throws Exception {
        log("Revision del buzon iniciada.");
        ControlReintentos reintentos=new ControlReintentos(valor("MAIL_RETRY_FILE",".correo-reintentos.properties"),Integer.parseInt(valor("MAIL_MAX_RETRIES","5")));
        String archivo = valor("MAIL_PROCESSED_FILE", "");
        java.nio.file.Path registro = archivo.isBlank() ? null : java.nio.file.Path.of(archivo);
        java.util.Set<String> atendidos = new java.util.HashSet<>();
        if (registro != null && java.nio.file.Files.exists(registro)) {
            atendidos.addAll(java.nio.file.Files.readAllLines(registro, StandardCharsets.UTF_8));
        }
        String protocolo = valor("MAIL_POP_PROTOCOL", "pop3");
        Properties props = new Properties();
        props.setProperty("mail." + protocolo + ".connectiontimeout", "10000");
        props.setProperty("mail." + protocolo + ".timeout", "30000");
        Store store = Session.getInstance(props).getStore(protocolo);
        try {
            store.connect(requerido("MAIL_POP_HOST"),
                    Integer.parseInt(valor("MAIL_POP_PORT", protocolo.equals("pop3s") ? "995" : "110")),
                    requerido("MAIL_POP_USER"), requerido("MAIL_POP_PASSWORD"));
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            log("POP3 conectado; mensajes en buzon: " + inbox.getMessageCount());
            try {
                for (Message message : inbox.getMessages()) {
                    String claveSolicitud=null;
                    try {
                        String filtro = valor("MAIL_MESSAGE_ID_FILTER", "");
                        if (!filtro.isBlank() && !filtro.equals(idMensaje(message))) continue;
                        if (automatico(message)) continue;
                        Address[] from = message.getFrom();
                        if (from == null || from.length != 1 || !(from[0] instanceof InternetAddress)) continue;
                        InternetAddress origen = (InternetAddress) from[0]; origen.validate();
                        String remitente = origen.getAddress();
                        String permitido = valor("MAIL_ALLOWED_SENDER", "");
                        if (!permitido.isBlank() && !permitido.equalsIgnoreCase(remitente)) continue;
                        String asunto = message.getSubject();
                        if (asunto == null) asunto="";
                        String id = idMensaje(message);
                        String clave = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest((remitente + "|" + id).getBytes(StandardCharsets.UTF_8)));
                        if (atendidos.contains(clave)) continue;
                        claveSolicitud=clave;
                        if(!reintentos.disponible(clave))continue;
                        log("Solicitud "+clave+" comando="+nombreComando(asunto));
                        // El asunto es la unica fuente de instrucciones para todos los casos.
                        String html = comandos.ejecutar(asunto, remitente, id);
                        log("Solicitud "+clave+" resultado="+(html.contains("data-resultado=\"error\"")?"RECHAZADA":"PROCESADA"));
                        responder(remitente, html, asunto, id);
                        if (registro != null) {
                            java.nio.file.Files.writeString(registro, clave + "\n", StandardCharsets.UTF_8,
                                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                            atendidos.add(clave);
                        }
                        reintentos.completado(clave);
                        // Eliminar solo despues de enviar. El backend reutiliza la respuesta si se reintenta.
                        if (Boolean.parseBoolean(valor("MAIL_DELETE_PROCESSED", "true"))) {
                            message.setFlag(Flags.Flag.DELETED, true);
                        }
                        log("Solicitud atendida; respuesta aceptada por SMTP (entrega final no confirmada).");
                    } catch (Exception e) {
                        RegistroServicio.error("Solicitud "+(claveSolicitud==null?"sin identificador":claveSolicitud)+" conservada",e);
                        if(claveSolicitud!=null)reintentos.fallo(claveSolicitud,e instanceof IllegalStateException || e instanceof jakarta.mail.AuthenticationFailedException);
                    }
                }
            } finally { inbox.close(true); }
            log("Revision del buzon finalizada.");
        } finally { if (store.isConnected()) store.close(); }
    }

    private static void log(String mensaje) {
        RegistroServicio.acceso(mensaje);
    }

    static boolean automatico(Message message) throws Exception {
        String[] valores = message.getHeader("Auto-Submitted");
        return valores != null && valores.length > 0 && !valores[0].equalsIgnoreCase("no");
    }

    static String idMensaje(Message message) throws Exception {
        String[] ids = message.getHeader("Message-ID");
        if (ids != null && ids.length > 0 && !ids[0].isBlank()) return ids[0];
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        message.writeTo(bytes);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }

    private void responder(String destinatario, String html, String asunto, String idOriginal) throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", requerido("MAIL_SMTP_HOST"));
        props.setProperty("mail.smtp.port", valor("MAIL_SMTP_PORT", "25"));
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "30000");
        props.setProperty("mail.smtp.writetimeout", "30000");
        props.setProperty("mail.smtp.starttls.enable", valor("MAIL_SMTP_STARTTLS", "false"));
        props.setProperty("mail.smtp.ssl.enable", valor("MAIL_SMTP_SSL", "false"));
        String user = valor("MAIL_SMTP_USER", "");
        props.setProperty("mail.smtp.auth", Boolean.toString(!user.isBlank()));
        Session session = Session.getInstance(props);
        MimeMessage respuesta = new MimeMessage(session);
        respuesta.setFrom(new InternetAddress(requerido("MAIL_FROM"), IdentidadProyecto.NOMBRE_CORTO, StandardCharsets.UTF_8.name()));
        respuesta.setRecipient(Message.RecipientType.TO, new InternetAddress(destinatario));
        respuesta.setSubject("Resultado " + nombreComando(asunto) + " - " + IdentidadProyecto.NOMBRE_CORTO, "UTF-8");
        if (idOriginal.matches("<[^<>\\s]+>")) {
            respuesta.setHeader("In-Reply-To", idOriginal);
            respuesta.setHeader("References", idOriginal);
        }
        respuesta.setHeader("Auto-Submitted", "auto-replied");
        respuesta.setContentLanguage(new String[]{"es-BO"});
        respuesta.setContent(html, "text/html; charset=UTF-8");
        log("Enviando respuesta por SMTP.");
        if (user.isBlank()) Transport.send(respuesta);
        else Transport.send(respuesta,user,requerido("MAIL_SMTP_PASSWORD"));
    }

    static String nombreComando(String asunto) {
        String comando=org.academia.dominio.MotorAcademico.normalizarAsunto(asunto).split("\\[",2)[0];
        return comando.matches("AYUDA|[A-Z]+ [A-Z]+") ? comando.toLowerCase(java.util.Locale.ROOT) : "solicitud";
    }

    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--listen")) args=new String[0];
        if(!(args.length==0 || (args.length==1 && (args[0].equals("--init")||args[0].equals("--once"))) || (args.length==4&&args[0].equals("--execute"))))
            throw new IllegalArgumentException("Use --listen, --once, --init o --execute ASUNTO REMITENTE ID.");
        ComandoEmail comandos = new ComandoEmail();
        comandos.inicializar();
        if (args.length == 1 && args[0].equals("--init")) { System.out.println("Base academica inicializada desde Java."); return; }
        if (args.length == 4 && args[0].equals("--execute")) {
            System.out.println(comandos.ejecutar(args[1],args[2],args[3])); return;
        }
        ServicioEmail servicio = new ServicioEmail();
        if (args.length == 1 && args[0].equals("--once")) { servicio.revisar(); return; }
        Configuracion.requerido("MAIL_POP_HOST"); Configuracion.requerido("MAIL_POP_USER");
        Configuracion.requerido("MAIL_POP_PASSWORD"); Configuracion.requerido("MAIL_SMTP_HOST"); Configuracion.requerido("MAIL_FROM");
        long intervalo = Long.parseLong(Configuracion.valor("MAIL_POLL_SECONDS", "15"));
        if (intervalo < 1) throw new IllegalArgumentException("MAIL_POLL_SECONDS debe ser positivo.");
        log(IdentidadProyecto.NOMBRE + " - Grupo " + IdentidadProyecto.GRUPO + ": servicio iniciado; intervalo=" + intervalo + "s.");
        while (!Thread.currentThread().isInterrupted()) {
            try { servicio.revisar(); }
            catch (Exception e) { RegistroServicio.error("Revision del buzon pendiente",e); }
            Thread.sleep(intervalo * 1000);
        }
    }
}
