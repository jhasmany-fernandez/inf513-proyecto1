package org.academia.correo;

import org.academia.datos.BaseDatos;
import org.academia.datos.Sql;
import org.academia.dominio.MotorAcademico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mindrot.jbcrypt.BCrypt;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EsquemaRemotoEspanolTest {
    private void habilitada() {
        Assumptions.assumeTrue("true".equals(System.getenv("ACADEMIA_PRUEBAS_REMOTAS")),
                "Verificacion remota opcional; las pruebas de escritura siempre hacen rollback.");
    }
    @Test void columnasRelacionesYClavesJsonEstanEnEspanol() throws Exception {
        habilitada();
        try(var c=BaseDatos.configurada().abrir()) {
            Sql db=new Sql(c);
            assertEquals(0,db.numero("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('email','password','remember_token','created_at','updated_at','migration','batch')"));
            // La base se comparte con la web: comprobar las tablas del correo,
            // sin exigir que no existan tablas adicionales de otros módulos.
            java.util.Set<String> esperadas=new java.util.HashSet<>();
            try(var recurso=BaseDatos.class.getResourceAsStream("/academia.sql")) {
                assertNotNull(recurso);
                var tablas=java.util.regex.Pattern.compile("CREATE TABLE public\\.([a-z_]+)")
                        .matcher(new String(recurso.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                while(tablas.find())esperadas.add(tablas.group(1));
            }
            assertEquals(23,esperadas.size());
            java.util.Set<String> actuales=new java.util.HashSet<>();
            for(var fila:db.filas("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"))
                actuales.add(fila.get("table_name").toString());
            esperadas.removeAll(actuales);
            assertTrue(esperadas.isEmpty(),"Faltan tablas del correo: "+esperadas);
            assertEquals(0,db.numero("SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated"));
            assertEquals(0,db.numero("SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname ~ '(email|password|remember_token|created_at|updated_at|migrations|_seq$|_pkey$|_unique$|_foreign$)'"));
            assertEquals(0,db.numero("SELECT count(*) FROM historial WHERE antes::text ~ '\"(email|password|remember_token|created_at|updated_at)\"\\s*:' OR despues::text ~ '\"(email|password|remember_token|created_at|updated_at)\"\\s*:'"));
            assertEquals(0,db.numero("SELECT count(*) FROM solicitudes_correo WHERE respuesta::text ~ '\"(email|password|remember_token|created_at|updated_at)\"\\s*:'"));
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> registro(MotorAcademico motor,String asunto) throws Exception {
        var respuesta=motor.ejecutar(asunto);
        return (Map<String,Object>)((List<?>)respuesta.get("datos")).get(0);
    }
    private long crear(MotorAcademico motor,String asunto) throws Exception {
        return ((Number)registro(motor,asunto).get("id")).longValue();
    }
    @Test void instalacionLimpiaYMigracionRepetidaEnEsquemaTemporal() throws Exception {
        habilitada();
        try(var c=BaseDatos.configurada().abrir()) {
            c.setAutoCommit(false);
            try {
                String esquema="prueba_espanol_"+UUID.randomUUID().toString().replace("-","");
                try(var s=c.createStatement()) {
                    s.execute("CREATE SCHEMA "+esquema);
                    for(String archivo:List.of("academia.sql","espanol.sql","normalizacion.sql","espanol.sql")) {
                        try(var recurso=BaseDatos.class.getResourceAsStream("/"+archivo)) {
                            assertNotNull(recurso);
                            String sql=new String(recurso.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
                                    .replace("public.",esquema+".").replace("'public'","'"+esquema+"'");
                            s.execute(sql);
                        }
                    }
                }
                Sql db=new Sql(c);
                assertEquals(23,db.numero("SELECT count(*) FROM information_schema.tables WHERE table_schema=?",esquema));
                assertEquals(0,db.numero("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND column_name IN ('email','password','remember_token','created_at','updated_at')",esquema));
            } finally { c.rollback(); }
        }
    }
    @Test void ochoCasosConCamposEspanolesSinConservarDatosDePrueba() throws Exception {
        habilitada();
        try(var c=BaseDatos.configurada().abrir()) {
            c.setAutoCommit(false);
            try {
                Sql db=new Sql(c);
                var actor=db.uno("SELECT * FROM usuarios WHERE rol='director' AND activo ORDER BY id LIMIT 1");
                assertNotNull(actor);
                MotorAcademico motor=new MotorAcademico(db,actor);
                String marca="QA-ESP-"+UUID.randomUUID();
                String correo="qa-"+UUID.randomUUID()+"@example.test";
                String clave="Clave  Segura123";
                LocalDate desde=LocalDate.now(ZoneId.of("America/La_Paz")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                LocalDate hasta=desde.plusDays(30);
                long docente=crear(motor,"usuario agregar [Docente de prueba;"+correo+";"+clave+";INSTRUCTOR;1]");
                assertTrue(BCrypt.checkpw(clave,db.uno("SELECT contrasena FROM usuarios WHERE id=?",docente).get("contrasena").toString()));
                var usuario=registro(motor,"usuario ver ["+docente+"]");
                assertEquals(correo,usuario.get("correo_electronico"));
                assertFalse(usuario.containsKey("contrasena"));
                assertFalse(usuario.containsKey("token_recordatorio"));
                assertNotNull(usuario.get("fecha_creacion"));
                motor.ejecutar("usuario modificar ["+docente+";Docente actualizado;"+correo+";;instructor;1]");
                assertTrue(BCrypt.checkpw(clave,db.uno("SELECT contrasena FROM usuarios WHERE id=?",docente).get("contrasena").toString()));
                long campana=crear(motor,"campa\u00f1a agregar ["+marca+";CORREO;"+desde+";"+hasta+"]");
                long convenio=crear(motor,"convenio agregar ["+marca+";Institucion de prueba;"+desde+";"+hasta+"]");
                long prospecto=crear(motor,"prospecto agregar [Jos\u00e9  P\u00e9rez;"+correo+";70000000;Correo;"+campana+";"+convenio+"]");
                crear(motor,"seguimiento agregar ["+prospecto+";"+desde+";Consulta de prueba]");
                long curso=crear(motor,"curso agregar ["+marca+";Descripcion;450;1]");
                long modulo=crear(motor,"modulo agregar ["+curso+";Modulo de prueba;20]");
                long grupo=crear(motor,"grupo agregar ["+modulo+";"+marca+";"+desde+";"+hasta+";10]");
                long oportunidad=crear(motor,"oportunidad agregar ["+prospecto+";"+curso+";nuevo]");
                for(String etapa:List.of("contactado","interesado"))motor.ejecutar("oportunidad modificar ["+oportunidad+";"+prospecto+";"+curso+";"+etapa+"]");
                long preinscripcion=crear(motor,"preinscripcion agregar ["+prospecto+";"+grupo+";confirmada]");
                motor.ejecutar("preinscripcion inscribir ["+preinscripcion+"]");
                long estudiante=db.numero("SELECT estudiante_id FROM matriculas WHERE preinscripcion_id=?",preinscripcion);
                assertEquals(correo,db.uno("SELECT correo_electronico FROM estudiantes WHERE id=?",estudiante).get("correo_electronico"));
                long tramite=crear(motor,"tramite agregar ["+estudiante+";TITULACI\u00d3N;solicitado;Matricula verificada;]");
                motor.ejecutar("tramite modificar ["+tramite+";"+estudiante+";titulacion;EN   REVISI\u00d3N;Matricula verificada;]");
                crear(motor,"personal agregar ["+docente+";"+marca.substring(0,20)+";Idiomas;Expediente de prueba]");
                long asignacion=crear(motor,"asignacion agregar ["+docente+";"+grupo+";60]");
                crear(motor,"horario agregar ["+asignacion+";1;08:00;10:00]");
                crear(motor,"asistencia agregar ["+asignacion+";"+desde+";08:15;10:00;]");
                long liquidacion=crear(motor,"asignacion liquidar ["+asignacion+";"+desde+";"+desde+"]");
                motor.ejecutar("liquidacion aprobar ["+liquidacion+"]");
                long criterio=crear(motor,"criterio agregar ["+marca+";50;5;1]");
                crear(motor,"evaluacion agregar ["+asignacion+";"+criterio+";"+desde+";4;Buen desempeno]");
                for(String tipo:org.academia.dominio.Catalogo.REPORTES)assertNotNull(motor.ejecutar("reporte "+tipo+" ["+desde+";"+hasta+"]"));
                for(String entidad:List.of("usuario","prospecto","curso","tramite","personal","asistencia","evaluacion","historial"))assertNotNull(motor.ejecutar(entidad+" mostrar"));
                long secretaria=crear(motor,"usuario agregar [Prueba baja;otra-"+correo+";"+clave+";secretaria;1]");
                motor.ejecutar("usuario eliminar ["+secretaria+"]");
                assertEquals(false,db.uno("SELECT activo FROM usuarios WHERE id=?",secretaria).get("activo"));
            } finally {
                c.rollback();
            }
        }
    }
}
