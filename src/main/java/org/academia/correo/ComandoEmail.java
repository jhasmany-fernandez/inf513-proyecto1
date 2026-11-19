package org.academia.correo;

import com.google.gson.Gson;
import org.academia.datos.BaseDatos;
import org.academia.datos.Sql;
import org.academia.dominio.ErrorComando;
import org.academia.dominio.MotorAcademico;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;

public final class ComandoEmail {
    private final BaseDatos.Conexion conexiones;
    private static final Gson JSON = new Gson();
    public ComandoEmail() { this(BaseDatos.configurada()); }
    public ComandoEmail(BaseDatos.Conexion conexiones) { this.conexiones = conexiones; }
    public void inicializar() throws Exception {
        BaseDatos.inicializar(conexiones);
        String correo_electronico = Configuracion.requerido("ACADEMIA_DIRECTOR_EMAIL").trim().toLowerCase(java.util.Locale.ROOT);
        String contrasena = Configuracion.requerido("ACADEMIA_DIRECTOR_PASSWORD");
        new jakarta.mail.internet.InternetAddress(correo_electronico).validate();
        if (contrasena.length() < 10 || contrasena.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("La clave del director requiere 10 caracteres y como maximo 72 bytes.");
        try (var c = conexiones.abrir()) {
            new Sql(c).ejecutar("INSERT INTO usuarios(nombre,correo_electronico,contrasena,rol,activo,fecha_creacion,fecha_actualizacion) VALUES ('Director',?,?, 'director',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (correo_electronico) DO NOTHING", correo_electronico, BCrypt.hashpw(contrasena, BCrypt.gensalt()));
        }
    }
    public String ejecutar(String asunto, String remitente, String messageId) throws IOException {
        if (remitente == null || messageId == null || messageId.isBlank()) return html(Map.of("error", "Remitente o identificador invalido."));
        try (var c = conexiones.abrir()) {
            c.setAutoCommit(false);
            try {
                Sql db = new Sql(c);
                db.uno("SELECT pg_advisory_xact_lock(16062026)");
                var actor = db.uno("SELECT * FROM usuarios WHERE lower(correo_electronico)=? AND activo=true", remitente.trim().toLowerCase(java.util.Locale.ROOT));
                if (actor == null) throw new ErrorComando("Remitente no autorizado o usuario inactivo.");
                String clave = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((actor.get("id") + "|" + messageId).getBytes(StandardCharsets.UTF_8)));
                var anterior = db.uno("SELECT respuesta FROM solicitudes_correo WHERE clave=?", clave);
                if (anterior != null) {
                    db.ejecutar("UPDATE solicitudes_correo SET usuario_id=?,autoria_pendiente=false WHERE clave=? AND usuario_id IS NULL",actor.get("id"),clave);
                    c.commit(); return html(com.google.gson.JsonParser.parseString(anterior.get("respuesta").toString()));
                }
                var respuesta = new MotorAcademico(db, actor).ejecutar(asunto);
                db.ejecutar("INSERT INTO solicitudes_correo(clave,respuesta,usuario_id,fecha_creacion,fecha_actualizacion) VALUES (?,?::json,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", clave, JSON.toJson(respuesta),actor.get("id"));
                c.commit(); return html(respuesta);
            } catch (ErrorComando e) { c.rollback(); return html(Map.of("error", e.getMessage())); }
            catch (SQLException e) {
                c.rollback();
                RegistroServicio.error("Fallo de base de datos al procesar solicitud",e);
                String estado=e.getSQLState()==null?"":e.getSQLState();
                if(estado.startsWith("23"))return html(Map.of("error","Registro duplicado, referencia inexistente o restriccion de datos incumplida."));
                if(estado.startsWith("22"))return html(Map.of("error","Valor fuera de rango o formato incompatible con la base de datos."));
                if(estado.startsWith("42"))return html(Map.of("error","La estructura de la base no coincide con el sistema. El administrador debe aplicar las migraciones; la operacion se revirtio."));
                throw new IOException("No se pudo procesar la solicitud en PostgreSQL.", e);
            } catch (IllegalArgumentException e) { c.rollback(); return html(Map.of("error","Parametros invalidos; revise formato y valores. Use ayuda.")); }
            catch (Exception e) { c.rollback(); throw new IOException("No se pudo procesar la solicitud academica.", e); }
        } catch (SQLException e) { throw new IOException("PostgreSQL no disponible; conserve el correo para reintento.", e); }
    }
    static String html(Object respuesta) {
        return "<!DOCTYPE html><html lang=\"es-BO\"><head><meta charset=\"UTF-8\"><meta http-equiv=\"Content-Language\" content=\"es-BO\"><title>" + escapar(IdentidadProyecto.NOMBRE_CORTO)
                + "</title><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>body{margin:0;background:#f3f5f7;color:#20252b;font:15px Arial,sans-serif;line-height:1.5}"
                + "main{max-width:880px;margin:24px auto;background:#fff;padding:24px;border:1px solid #dce1e6}"
                + "h1{font-size:22px;line-height:1.3;color:#176b58;margin:0 0 12px}"
                + "dt{font-weight:bold;color:#374151;margin-top:12px}dd{margin:4px 0;overflow-wrap:anywhere}"
                + "ol{padding-left:24px}li{padding-bottom:12px;border-bottom:1px solid #e5e7eb}"
                + "footer{margin-top:24px;padding-top:12px;border-top:1px solid #dce1e6;color:#59636e;font-size:12px}"
                + "@media(max-width:600px){main{margin:0;padding:16px;border:0}h1{font-size:20px}}</style>"
                + "</head><body lang=\"es-BO\"><div style=\"max-width:880px;margin:16px auto;padding:24px;border:1px solid #dce1e6;"
                + "background:#ffffff;color:#20252b;font:15px Arial,sans-serif;line-height:1.5;overflow-wrap:anywhere\">"
                + "<h1 style=\"font:700 22px Arial,sans-serif;line-height:1.3;color:#176b58;margin:0 0 12px\">"
                + escapar(IdentidadProyecto.NOMBRE) + "</h1><p>Grupo "
                + IdentidadProyecto.GRUPO + "</p>" + presentarRespuesta(JSON.toJsonTree(respuesta))
                + "<footer><p style=\"margin-top:24px;padding-top:12px;border-top:1px solid #dce1e6;color:#59636e;font-size:12px\">"
                + "Respuesta autom\u00e1tica del sistema acad\u00e9mico. Para realizar otra solicitud, env\u00ede un nuevo correo "
                + "con el comando en el asunto. Escriba ayuda o help para consultar los comandos disponibles seg\u00fan su rol.</p></footer></div></body></html>";
    }
    private static final String TABLA = "<table cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;font:14px Arial,sans-serif;color:#20252b;line-height:1.5;margin:12px 0\">";
    private static final String CELDA = "style=\"padding:10px 12px;border:1px solid #dce1e6;text-align:left;vertical-align:top;overflow-wrap:anywhere;word-break:break-word\"";

    private static String presentarRespuesta(com.google.gson.JsonElement valor) {
        if (!valor.isJsonObject()) return presentar(valor);
        var objeto = valor.getAsJsonObject();
        boolean error = objeto.has("error");
        StringBuilder b = new StringBuilder("<div data-resultado=\"").append(error ? "error" : "exito")
                .append("\" style=\"padding:12px 16px;border-left:4px solid ")
                .append(error ? "#a12b35;background:#fff1f2" : "#176b58;background:#edf7f3")
                .append(";margin:16px 0\"><strong>").append(error ? "Solicitud no procesada" : "Resultado de la solicitud")
                .append("</strong>");
        String clave = error ? "error" : "mensaje";
        if (objeto.has(clave)) b.append("<br>").append(presentar(objeto.get(clave)));
        b.append("</div>");
        if (objeto.has("datos")) b.append(presentar(objeto.get("datos")));
        var resumen = objeto.deepCopy();
        resumen.remove("error"); resumen.remove("mensaje"); resumen.remove("datos");
        if (!resumen.isEmpty()) b.append(presentar(resumen));
        return b.toString();
    }

    private static String presentar(com.google.gson.JsonElement valor) {
        if (valor.isJsonObject()) {
            if (valor.getAsJsonObject().has("formulario_html")) {
                var copia=valor.getAsJsonObject().deepCopy(); var html=copia.remove("formulario_html");
                StringBuilder b=new StringBuilder(); if(!copia.isEmpty()) b.append(presentar(copia));
                return b.append(html.getAsString()).toString();
            }
            StringBuilder b = new StringBuilder(TABLA).append("<thead><tr style=\"background:#eef1f4\"><th scope=\"col\" ").append(CELDA)
                    .append(">Campo</th><th scope=\"col\" ").append(CELDA).append(">Detalle</th></tr></thead><tbody>");
            valor.getAsJsonObject().entrySet().forEach(e -> b.append("<tr><th scope=\"row\" ").append(CELDA)
                    .append("><span style=\"font-weight:600\">").append(escapar(etiqueta(e.getKey())))
                    .append("</span></th><td ").append(CELDA).append(">").append(presentar(e.getValue())).append("</td></tr>"));
            return b.append("</tbody></table>").toString();
        }
        if (valor.isJsonArray()) {
            var filas = valor.getAsJsonArray();
            if (filas.isEmpty()) return "<p style=\"color:#59636e\">No hay registros para mostrar.</p>";
            if (filas.size() == 1) return presentar(filas.get(0));
            boolean objetos = true;
            var columnas = new java.util.LinkedHashSet<String>();
            for (var fila : filas) {
                if (!fila.isJsonObject()) { objetos = false; break; }
                columnas.addAll(fila.getAsJsonObject().keySet());
            }
            if (objetos) {
                if(columnas.containsAll(java.util.List.of("caso","comando","parametros"))){
                    var ordenadas=new java.util.LinkedHashSet<String>(java.util.List.of("caso","comando","parametros"));
                    ordenadas.addAll(columnas);columnas=ordenadas;
                }
                StringBuilder b = new StringBuilder("<div style=\"overflow-x:auto\">").append(TABLA)
                        .append("<thead><tr style=\"background:#eef1f4\">");
                for (String columna : columnas) b.append("<th scope=\"col\" ").append(CELDA).append(">")
                        .append(escapar(etiqueta(columna))).append("</th>");
                b.append("</tr></thead><tbody>");
                int indice = 0;
                for (var fila : filas) {
                    b.append("<tr style=\"background:").append(indice++ % 2 == 0 ? "#ffffff" : "#f7f9fa").append("\">");
                    for (String columna : columnas) b.append("<td ").append(CELDA).append(">")
                            .append(fila.getAsJsonObject().has(columna) ? presentar(fila.getAsJsonObject().get(columna)) : "&mdash;").append("</td>");
                    b.append("</tr>");
                }
                return b.append("</tbody></table></div>").toString();
            }
            StringBuilder b = new StringBuilder(TABLA).append("<tbody>");
            for (var fila : filas) b.append("<tr><td ").append(CELDA).append(">").append(presentar(fila)).append("</td></tr>");
            return b.append("</tbody></table>").toString();
        }
        if (valor.isJsonNull()) return "&mdash;";
        if (valor.getAsJsonPrimitive().isBoolean()) return valor.getAsBoolean() ? "Si" : "No";
        return escapar(valor.getAsString());
    }
    private static String etiqueta(String nombre) {
        return switch (nombre) {
            case "id" -> "ID";
            case "estudiante_id" -> "ID del estudiante";
            case "fecha_creacion" -> "Fecha de registro";
            case "fecha_actualizacion" -> "Ultima actualizacion";
            case "calidad_porcentaje" -> "Calidad (%)";
            case "parametros" -> "Par\u00e1metros y ejemplos";
            default -> Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1).replace('_', ' ');
        };
    }
    private static String escapar(Object valor) {
        return (valor == null ? "" : valor.toString()).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}
