package org.academia.datos;

import org.academia.correo.Configuracion;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public final class BaseDatos {
    @FunctionalInterface public interface Conexion { Connection abrir() throws SQLException; }
    private BaseDatos() {}

    public static Conexion configurada() {
        String url=Configuracion.requerido("DB_URL");
        String usuario=Configuracion.requerido("DB_USERNAME");
        String clave=Configuracion.valor("DB_PASSWORD",Configuracion.valor("ACADEMIA_DB_PASSWORD",""));
        if(clave.isBlank())throw new IllegalStateException("Configure DB_PASSWORD o ACADEMIA_DB_PASSWORD.");
        return ()->DriverManager.getConnection(url,usuario,clave);
    }

    public static void inicializar(Conexion fabrica) throws SQLException,IOException {
        try(Connection c=fabrica.abrir()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='10s'");s.execute("SET LOCAL statement_timeout='60s'");}
                try(var s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(16062026)");}
                boolean existe;
                try(var s=c.createStatement();var rs=s.executeQuery("SELECT to_regclass('public.usuarios') IS NOT NULL")){rs.next();existe=rs.getBoolean(1);}
                if(!existe)try(var recurso=BaseDatos.class.getResourceAsStream("/academia.sql")){
                    if(recurso==null)throw new IOException("Falta el esquema academico.");
                    try(var s=c.createStatement()){s.execute(new String(recurso.readAllBytes(),StandardCharsets.UTF_8));}
                }
                try(var recurso=BaseDatos.class.getResourceAsStream("/espanol.sql")) {
                    if(recurso==null)throw new IOException("Falta la migracion de campos al espanol.");
                    try(var s=c.createStatement()){s.execute(new String(recurso.readAllBytes(),StandardCharsets.UTF_8));}
                }
                try(var recurso=BaseDatos.class.getResourceAsStream("/normalizacion.sql")) {
                    if(recurso==null)throw new IOException("Falta la migracion de normalizacion.");
                    try(var s=c.createStatement()){s.execute(new String(recurso.readAllBytes(),StandardCharsets.UTF_8));}
                }
                try(var recurso=BaseDatos.class.getResourceAsStream("/relaciones_correo.sql")) {
                    if(recurso==null)throw new IOException("Falta la migracion de relaciones de pagos y solicitudes.");
                    try(var s=c.createStatement()){s.execute(new String(recurso.readAllBytes(),StandardCharsets.UTF_8));}
                }
                c.commit();
            }catch(SQLException|IOException e){c.rollback();throw e;}
        }
    }
}
