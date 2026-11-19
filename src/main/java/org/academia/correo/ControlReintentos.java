package org.academia.correo;

import java.nio.file.*;
import java.io.*;
import java.util.Properties;

/** Conserva mensajes sin borrarlos y pausa los que exceden el limite. */
final class ControlReintentos {
    private final Properties datos=new Properties();
    private final Path archivo;
    private final int maximo;
    ControlReintentos(String ruta,int maximo) throws IOException {
        if(maximo<1)throw new IllegalArgumentException("MAIL_MAX_RETRIES debe ser positivo.");
        this.archivo=Path.of(ruta);this.maximo=maximo;
        if(Files.exists(archivo))try(var entrada=Files.newInputStream(archivo)){datos.load(entrada);}
    }
    boolean disponible(String clave) {
        String[] partes=datos.getProperty(clave,"0:0").split(":");
        return Integer.parseInt(partes[0])<maximo && System.currentTimeMillis()>=Long.parseLong(partes[1]);
    }
    void fallo(String clave,boolean permanente) throws IOException {
        int n=Integer.parseInt(datos.getProperty(clave,"0:0").split(":")[0])+1;
        if(permanente)n=maximo;
        long demora=Math.min(3600L,30L*(1L<<Math.min(n-1,7)))*1000;
        datos.setProperty(clave,n+":"+(System.currentTimeMillis()+demora));guardar();
        RegistroServicio.acceso("Solicitud "+clave+" intentos="+n+" estado="+(n>=maximo?"PAUSADA; requiere revision manual":"REINTENTO DIFERIDO"));
    }
    void completado(String clave) throws IOException {if(datos.remove(clave)!=null)guardar();}
    private void guardar() throws IOException {
        Path destino=archivo.toAbsolutePath();Files.createDirectories(destino.getParent());
        Path temporal=Files.createTempFile(destino.getParent(),"reintentos-",".tmp");
        try {
            try(var salida=Files.newOutputStream(temporal)){datos.store(salida,"Intentos y fecha del proximo intento; sin contenido de correos");}
            Files.move(temporal,destino,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporal);}
    }
}
