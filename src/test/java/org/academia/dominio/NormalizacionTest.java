package org.academia.dominio;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NormalizacionTest {
    private final MotorAcademico motor=new MotorAcademico(null, Map.of("rol","director"));

    @Test void ayudaAgrupaCasosYExplicaPeriodosSinCambiarPermisos() throws Exception {
        for(String rol:java.util.List.of("director","secretaria","instructor")){
            var ayuda=(java.util.List<?>)new MotorAcademico(null,Map.of("rol",rol)).ejecutar("ayuda").get("datos");
            int anterior=0;
            for(Object objeto:ayuda){
                var fila=(Map<?,?>)objeto;
                int caso=Integer.parseInt(fila.get("caso").toString().substring(2));
                assertTrue(caso>=anterior,"Los casos deben permanecer agrupados");anterior=caso;
                if(fila.get("comando").toString().startsWith("reporte ")){
                    assertEquals(8,caso);
                    assertTrue(fila.get("parametros").toString().contains("Fechas opcionales"));
                    assertTrue(fila.get("parametros").toString().contains("[2026-10-01; 2026-10-31]"));
                    assertNotEquals("instructor",rol);
                }
                if(rol.equals("secretaria"))assertNotEquals(1,caso);
            }
        }
    }

    @Test void asuntosUsanEntidadYAccionEnEspanol() throws Exception {
        String[][] casos={
            {"  UsU\u00c1rio   MOSTRAR  ","USUARIO MOSTRAR[*]"},
            {"usuarios mostrar [ 2 ]","USUARIO MOSTRAR[ 2 ]"},
            {"campa\u00f1a crear [Jos\u00e9  P\u00e9rez,correo,2026-10-18,2026-10-31]","CAMPANA AGREGAR[Jos\u00e9  P\u00e9rez,correo,2026-10-18,2026-10-31]"},
            {"curso obtener [1]","CURSO VER[1]"},
            {"tr\u00e1mite actualizar [2,x]","TRAMITE MODIFICAR[2,x]"},
            {"personal mostrar","PERSONAL MOSTRAR[*]"},
            {"asignaci\u00f3n liquidar [2,2026-10-01,2026-10-18]","ASIGNACION LIQUIDAR[2,2026-10-01,2026-10-18]"},
            {"liquidaci\u00f3n aprobar [1]","LIQUIDACION APROBAR[1]"},
            {"preinscripci\u00f3n inscribir [1]","PREINSCRIPCION INSCRIBIR[1]"},
            {"evaluaci\u00f3n mostrar","EVALUACION MOSTRAR[*]"},
            {"convenio eliminar [1]","CONVENIO ELIMINAR[1]"},
            {"reporte general","REPORTE GENERAL[*]"}};
        for(var caso:casos)assertEquals(caso[1],MotorAcademico.normalizarAsunto(caso[0]),caso[0]);
        assertEquals(motor.ejecutar("HELP"),motor.ejecutar("Ay\u00fada"));
        String ayuda=motor.ejecutar("ayuda").toString();
        assertTrue(ayuda.contains("usuario mostrar"));
        assertFalse(ayuda.contains("USUARIO AGREGAR"));
    }

    @Test void formatoDelModeloYParametrosPuntoYComa() throws Exception {
        assertEquals("USUARIO AGREGAR[Jos\u00e9; correo@test.com; Clave  Segura1; DIRECTOR; TRUE]",MotorAcademico.normalizarAsunto("usuario agregar [Jos\u00e9; correo@test.com; Clave  Segura1; DIRECTOR; TRUE]"));
        assertEquals("USUARIO VER[25]",MotorAcademico.normalizarAsunto("usuario ver [25]"));
        assertEquals("USUARIO MODIFICAR[25; Jos\u00e9]",MotorAcademico.normalizarAsunto("usuario modificar [25; Jos\u00e9]"));
        assertEquals(java.util.List.of("Jos\u00e9, P\u00e9rez","texto; con separador","Clave  Segura1"),parametrosModelo());
        String ayuda=motor.ejecutar(" usuario AYUDA ").toString();
        assertTrue(ayuda.contains("usuario agregar [nombre; correo_electronico; contrasena; rol; activo]"));
        assertFalse(ayuda.contains("curso agregar"));
        assertTrue(ayuda.contains("Baja logica"));
        var secretaria=new MotorAcademico(null,Map.of("rol","secretaria"));
        assertThrows(ErrorComando.class,()->secretaria.ejecutar("usuario ayuda"));
        assertThrows(ErrorComando.class,()->motor.ejecutar("usuario agregar [Nombre; correo@test.com]"));
        assertThrows(ErrorComando.class,()->motor.ejecutar("asignacion liquidar [1; 2026-10-01]"));
    }
    private java.util.List<String> parametrosModelo() throws Exception {
        return MotorAcademico.parametros("Jos\u00e9, P\u00e9rez; \"texto; con separador\"; Clave  Segura1",';');
    }

    @Test void comandosIgnoranEspaciosAcentosYMayusculas() throws Exception {
        assertEquals("CAMPANA MOSTRAR[ * ]", MotorAcademico.normalizarAsunto("  Camp\u00e1\u00f1as   MoStRaR [ * ]  "));
        assertEquals("REPORTE TRAMITES[*]", MotorAcademico.normalizarAsunto("\u00a0reP\u00f3rte\u00a0Tr\u00e1mites[*]\u00a0"));
        assertEquals(motor.ejecutar("HELP"),motor.ejecutar("  h\u00c9lp  "));
        assertThrows(ErrorComando.class,()->motor.ejecutar("HELP\n"));
        assertThrows(ErrorComando.class,()->motor.ejecutar("cualquier cosa[*]"));
    }

    @Test void csvNoPierdeComasAcentosNiEspaciosDeClave() throws Exception {
        String csv="\"Jos\u00e9,  P\u00e9rez\",correo@test.com,Clave  Con   Espacios1,DiReCtOr,TRUE";
        assertEquals("USUARIO AGREGAR["+csv+"]",MotorAcademico.normalizarAsunto(" Usuarios agr\u00e9gar ["+csv+"] "));
        var valores=MotorAcademico.parametros(csv);
        var campos=Catalogo.obtener("USUARIOS").campos();
        assertEquals("Jos\u00e9, P\u00e9rez",motor.validarCampo(campos.get(0),valores.get(0)));
        assertEquals("Clave  Con   Espacios1",motor.validarCampo(campos.get(2),valores.get(2)));
        assertEquals("director",motor.validarCampo(campos.get(3),valores.get(3)));
        assertEquals(true,motor.validarCampo(campos.get(4),valores.get(4)));
    }

    @Test void estadosSeNormalizanPeroValoresInvalidosSeRechazan() throws Exception {
        var campos=Catalogo.obtener("TRAMITES").campos();
        assertEquals("titulacion",motor.validarCampo(campos.get(1)," TITULACI\u00d3N "));
        assertEquals("en_revision",motor.validarCampo(campos.get(2)," En   Revisi\u00f3n "));
        assertThrows(ErrorComando.class,()->motor.validarCampo(campos.get(2),"estado desconocido"));
    }

    @Test void rechazaComandosPegadosEnInglesOConOrdenAnterior() {
        for(String asunto:java.util.List.of("UPDATECAMPANAS[4,x]","CREATEUSUARIOS[x]","LISTARUSUARIOS[*]",
                "GETUSUARIOS[1]","DELETECAMPANAS[1]","REPORTEGENERAL[*]","INSCRIBIR[1]",
                "LIQUIDAR[1,2026-10-01,2026-10-18]","APROBAR[1]","usuario update [1]",
                "usuario create [x]","listar usuarios[*]","usuariomostrar","helpusuarios","h e l p")){
            assertThrows(ErrorComando.class,()->motor.ejecutar(asunto),asunto);
        }
    }
}
