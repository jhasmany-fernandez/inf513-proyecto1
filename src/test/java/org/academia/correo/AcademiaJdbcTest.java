package org.academia.correo;

import org.academia.datos.BaseDatos;
import org.academia.datos.Sql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AcademiaJdbcTest {
    private BaseDatos.Conexion conexiones;
    private ComandoEmail comandos;
    @BeforeEach void preparar() throws Exception {
        String url=System.getenv("ACADEMIA_TEST_DB_URL");
        Assumptions.assumeTrue(url!=null,"Configure una base PostgreSQL de pruebas.");
        assertTrue(url.endsWith("/academia_java_test"),"Se prohibe limpiar una base que no sea de pruebas.");
        conexiones=()->DriverManager.getConnection(url,"academia",System.getenv("DB_PASSWORD"));
        BaseDatos.inicializar(conexiones);
        try(var c=conexiones.abrir()) {
            new Sql(c).ejecutar("TRUNCATE usuarios,solicitudes_correo,cursos,campanas,convenios,prospectos,criterios RESTART IDENTITY CASCADE");
            new Sql(c).ejecutar("INSERT INTO usuarios(nombre,correo_electronico,contrasena,rol,activo) VALUES ('Director','director@academia.test','test','director',true)");
        }
        comandos=new ComandoEmail(conexiones);
    }
    private String ejecutar(String asunto) throws Exception {
        String html=comandos.ejecutar(asunto,"director@academia.test",UUID.randomUUID().toString());
        assertFalse(html.contains("data-resultado=\"error\""),html);return html;
    }
    private void error(String asunto) throws Exception {
        assertTrue(comandos.ejecutar(asunto,"director@academia.test",UUID.randomUUID().toString()).contains("data-resultado=\"error\""));
    }
    @Test void eightUseCasesWithEnrollmentAttendanceSettlementAndReports() throws Exception {
        ejecutar("USUARIO AGREGAR[Docente,docente@academia.test,ClaveSegura123,instructor,1]");
        ejecutar("CAMPANA AGREGAR[Idiomas,facebook,2026-09-01,2026-10-31]");
        ejecutar("CONVENIO AGREGAR[Convenio,Universidad,2026-09-01,2026-10-31]");
        ejecutar("PROSPECTO AGREGAR[Ana,ana@academia.test,70000000,correo,1,1]");
        ejecutar("SEGUIMIENTO AGREGAR[1,2026-10-01,Consulta atendida]");
        ejecutar("CURSO AGREGAR[Ingles,Idiomas,450,1]");
        ejecutar("MODULO AGREGAR[1,Basico,20]");
        ejecutar("GRUPO AGREGAR[1,Octubre,2026-10-01,2026-10-31,1]");
        ejecutar("OPORTUNIDAD AGREGAR[1,1,nuevo]");
        error("OPORTUNIDAD MODIFICAR[1,1,1,inscrito]");
        ejecutar("OPORTUNIDAD MODIFICAR[1,1,1,contactado]");
        ejecutar("OPORTUNIDAD MODIFICAR[1,1,1,interesado]");
        ejecutar("PREINSCRIPCION AGREGAR[1,1,confirmada]");
        ejecutar("OPORTUNIDAD MODIFICAR[1,1,1,preinscrito]");
        ejecutar("PREINSCRIPCION INSCRIBIR[1]");
        error("PREINSCRIPCION INSCRIBIR[1]");
        ejecutar("TRAMITE AGREGAR[1,titulacion,solicitado,Matricula,]");
        error("TRAMITE MODIFICAR[1,1,titulacion,entregado,Matricula,]");
        ejecutar("TRAMITE MODIFICAR[1,1,titulacion,en_revision,Matricula,]");
        ejecutar("TRAMITE MODIFICAR[1,1,titulacion,aprobado,Matricula,]");
        ejecutar("TRAMITE MODIFICAR[1,1,titulacion,entregado,Matricula,]");
        ejecutar("PERSONAL AGREGAR[2,123456,Idiomas,Referencia expediente]");
        ejecutar("ASIGNACION AGREGAR[2,1,60]");
        ejecutar("HORARIO AGREGAR[1,1,08:00,10:00]");
        error("HORARIO AGREGAR[1,1,09:00,11:00]");
        ejecutar("ASISTENCIA AGREGAR[1,2026-10-05,08:15,10:00,]");
        assertTrue(ejecutar("ASIGNACION LIQUIDAR[1,2026-10-01,2026-10-05]").contains("105.00"));
        error("ASIGNACION LIQUIDAR[1,2026-10-01,2026-10-05]");
        error("ASISTENCIA ELIMINAR[1]");
        ejecutar("LIQUIDACION APROBAR[1]");
        ejecutar("CRITERIO AGREGAR[Calidad,50,5,1]");
        ejecutar("EVALUACION AGREGAR[1,1,2026-10-05,4,Buen desempeno]");
        error("CRITERIO MODIFICAR[1,Calidad,70,10,1]");
        ejecutar("CRITERIO MODIFICAR[1,Calidad,50,5,0]");
        assertTrue(ejecutar("REPORTE EVALUACIONES[*]").contains("80.00"));
        for(String reporte:org.academia.dominio.Catalogo.REPORTES) ejecutar("REPORTE "+reporte+"[2026-10-01,2026-10-06]");
        ejecutar("HISTORIAL MOSTRAR[*]");
        assertFalse(comandos.ejecutar("ASIGNACION MOSTRAR[*]","docente@academia.test","own").contains("data-resultado=\"error\""));
        assertTrue(comandos.ejecutar("PROSPECTO MOSTRAR[*]","docente@academia.test","denied").contains("data-resultado=\"error\""));
    }
    @Test void retriesDoNotDuplicateChangesAndInvalidRequestsRollback() throws Exception {
        String asunto="CURSO AGREGAR[\"Curso, nivel 1\",Descripcion,300,1]";
        String primera=comandos.ejecutar(asunto,"director@academia.test","same-id");
        assertEquals(primera,comandos.ejecutar(asunto,"director@academia.test","same-id"));
        error("CURSO AGREGAR[Curso,Descripcion,-1,1]");
        error("CURSO AGREGAR[\"incompleto]");
        try(var c=conexiones.abrir()) { assertEquals(1,new Sql(c).numero("SELECT count(*) FROM cursos")); }
        assertTrue(comandos.ejecutar("HELP","desconocido@academia.test","unknown").contains("data-resultado=\"error\""));
    }
    @Test void schemaDoesNotDuplicateEnrollmentGroupOrEvaluationScale() throws Exception {
        // La URL de pruebas fue validada antes de permitir una instalacion limpia.
        try(var c=conexiones.abrir()) {
            new Sql(c).ejecutar("DROP SCHEMA public CASCADE");
            new Sql(c).ejecutar("CREATE SCHEMA public");
        }
        BaseDatos.inicializar(conexiones);
        BaseDatos.inicializar(conexiones);
        try(var c=conexiones.abrir()) {
            assertEquals(0,new Sql(c).numero("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='matriculas' AND column_name='grupo_id') OR (table_name='evaluaciones' AND column_name IN ('peso','puntuacion_max')))"));
        }
    }

    @Test void paymentsAndMailRequestsHaveConsistentOwnersAndConcepts() throws Exception {
        ejecutar("ESTUDIANTE AGREGAR[Ana;ana@academia.test;70000000;]");
        ejecutar("ESTUDIANTE AGREGAR[Luz;luz@academia.test;70000001;]");
        ejecutar("TRAMITE AGREGAR[1;certificado;solicitado;Requisitos;]");
        ejecutar("PROSPECTO AGREGAR[Prospecto;prospecto@academia.test;70000000;correo;;]");
        ejecutar("CURSO AGREGAR[Curso;Descripcion;100;1]");
        ejecutar("MODULO AGREGAR[1;Modulo;20]");
        ejecutar("GRUPO AGREGAR[1;Grupo;2026-10-01;2026-10-31;10]");
        ejecutar("PREINSCRIPCION AGREGAR[1;1;confirmada]");
        ejecutar("PREINSCRIPCION INSCRIBIR[1]");
        ejecutar("pago generar [70000000;PRUEBA-TRAMITE;0.01;Certificado;trámite;1]");
        ejecutar("pago generar [70000000;PRUEBA-MATRICULA;100;Inscripción;matrícula;1]");
        error("pago generar [70000000;SIN-CONCEPTO;0.01;Sin concepto]");
        error("pago generar [70000000;INEXISTENTE;0.01;Prueba;tramite;99999]");
        error("pago generar [70000000;PRUEBA-TRAMITE;20;Duplicado;tramite;1]");
        error("pago generar [70000000;CERO;0;Invalido;tramite;1]");
        try(var c=conexiones.abrir()) {
            Sql db=new Sql(c);
            assertEquals(0,db.numero("SELECT count(*) FROM solicitudes_correo WHERE usuario_id IS NULL"));
            assertEquals(2,db.numero("SELECT count(*) FROM pagos WHERE usuario_id=1 AND pendiente_vinculacion=false"));
            assertEquals(new java.math.BigDecimal("0.01"),db.uno("SELECT monto FROM pagos WHERE pedido_id='PRUEBA-TRAMITE'").get("monto"));
            assertThrows(java.sql.SQLException.class,()->db.ejecutar("UPDATE pagos SET estudiante_id=2 WHERE pedido_id='PRUEBA-TRAMITE'"));
            assertThrows(java.sql.SQLException.class,()->db.ejecutar("UPDATE pagos SET matricula_id=1 WHERE pedido_id='PRUEBA-TRAMITE'"));
            db.ejecutar("INSERT INTO pagos(pedido_id,correo_electronico,monto,usuario_id,pendiente_vinculacion) VALUES ('HISTORICO','director@academia.test',1,1,true)");
        }
        ejecutar("pago vincular [HISTORICO;tramite;1]");
        error("pago vincular [HISTORICO;tramite;1]");
        BaseDatos.inicializar(conexiones);
        try(var c=conexiones.abrir()) {
            assertEquals(3,new Sql(c).numero("SELECT count(*) FROM pagos WHERE pendiente_vinculacion=false"));
        }
    }
}
