package org.academia.correo;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Registro sin parametros del asunto, cuerpos, credenciales ni mensajes SQL. */
final class RegistroServicio {
    private RegistroServicio() {}
    static void acceso(String evento) { escribir("access.log", "INFO", evento, null); }
    static void error(String evento, Throwable error) { escribir("error.log", "ERROR", evento, error); }
    private static synchronized void escribir(String archivo,String nivel,String evento,Throwable error) {
        StringBuilder linea=new StringBuilder(java.time.OffsetDateTime.now()+" "+nivel+" "+evento.replaceAll("[\\r\\n]"," "));
        if(error!=null) {
            Throwable causa=error;
            for(int i=0;causa!=null&&i<6;i++,causa=causa.getCause()) {
                linea.append(" causa=").append(causa.getClass().getSimpleName());
                if(causa instanceof java.sql.SQLException sql)linea.append(" SQLState=").append(sql.getSQLState());
                for(StackTraceElement marco:causa.getStackTrace())
                    if(marco.getClassName().startsWith("org.academia."))linea.append(" en ").append(marco);
            }
        }
        String texto=linea+System.lineSeparator();
        if(error==null)System.out.print(texto);else System.err.print(texto);
        try {
            Path carpeta=Path.of(Configuracion.valor("MAIL_LOG_DIR","logs"));
            Files.createDirectories(carpeta);
            Path destino=carpeta.resolve(archivo);
            if(Files.exists(destino)&&Files.size(destino)>5_000_000)
                Files.move(destino,carpeta.resolve(archivo+".1"),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(destino,texto,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(IOException e) { System.err.println("No se pudo escribir el registro local: "+e.getClass().getSimpleName()); }
    }
}
