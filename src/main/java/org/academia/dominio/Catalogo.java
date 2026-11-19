package org.academia.dominio;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Catalogo {
    public enum Tipo { TEXTO, CORREO_ELECTRONICO, CONTRASENA, ENTERO, DECIMAL, BOOLEANO, FECHA, HORA, OPCION, REFERENCIA }
    public record Campo(String nombre, Tipo tipo, boolean opcional, double minimo, double maximo, List<String> opciones, String tabla) {}
    public record Entidad(int caso, String tabla, List<Campo> campos) {}
    public static final Map<String, Entidad> ENTIDADES = new LinkedHashMap<>();
    public static final List<String> REPORTES = List.of("GENERAL","CAPTACION","CRM","TRAMITES","ASISTENCIA","HONORARIOS","EVALUACIONES");

    private static Campo campo(String n, Tipo t, boolean o, double min, double max) { return new Campo(n,t,o,min,max,List.of(),null); }
    private static Campo texto(String n, int max) { return campo(n,Tipo.TEXTO,false,1,max); }
    private static Campo opcional(String n, int max) { return campo(n,Tipo.TEXTO,true,0,max); }
    private static Campo correo_electronico() { return campo("correo_electronico",Tipo.CORREO_ELECTRONICO,false,1,150); }
    private static Campo bool(String n) { return campo(n,Tipo.BOOLEANO,false,0,1); }
    private static Campo entero(String n,int min,int max) { return campo(n,Tipo.ENTERO,false,min,max); }
    private static Campo decimal(String n,double min,double max) { return campo(n,Tipo.DECIMAL,false,min,max); }
    private static Campo fecha(String n) { return campo(n,Tipo.FECHA,false,0,0); }
    private static Campo hora(String n) { return campo(n,Tipo.HORA,false,0,0); }
    private static Campo ref(String n,String tabla,boolean opcional) { return new Campo(n,Tipo.REFERENCIA,opcional,1,Long.MAX_VALUE,List.of(),tabla); }
    private static Campo opcion(String n,String... valores) { return new Campo(n,Tipo.OPCION,false,0,0,List.of(valores),null); }
    private static Campo canal() { return opcion("canal","correo","whatsapp","facebook","telefono","presencial","web","otro"); }
    private static void entidad(String nombre,int caso,String tabla,Campo... campos) { ENTIDADES.put(nombre,new Entidad(caso,tabla,List.of(campos))); }

    static {
        entidad("USUARIOS",1,"usuarios",texto("nombre",150),correo_electronico(),campo("contrasena",Tipo.CONTRASENA,false,10,200),opcion("rol","director","instructor","secretaria"),bool("activo"));
        entidad("CAMPANAS",2,"campanas",texto("nombre",150),canal(),fecha("inicio"),fecha("fin"));
        entidad("CONVENIOS",2,"convenios",texto("nombre",150),texto("institucion",200),fecha("inicio"),fecha("fin"));
        entidad("PROSPECTOS",2,"prospectos",texto("nombre",150),correo_electronico(),texto("telefono",30),canal(),ref("campana_id","campanas",true),ref("convenio_id","convenios",true));
        entidad("SEGUIMIENTOS",2,"seguimientos",ref("prospecto_id","prospectos",false),fecha("fecha"),texto("descripcion",2000));
        entidad("CURSOS",3,"cursos",texto("nombre",150),texto("descripcion",2000),decimal("costo",0,1000000),bool("activo"));
        entidad("MODULOS",3,"modulos",ref("curso_id","cursos",false),texto("nombre",150),entero("horas",1,1000));
        entidad("GRUPOS",3,"grupos",ref("modulo_id","modulos",false),texto("nombre",150),fecha("inicio"),fecha("fin"),entero("cupo",1,1000));
        entidad("PREINSCRIPCIONES",3,"preinscripciones",ref("prospecto_id","prospectos",false),ref("grupo_id","grupos",false),opcion("estado","pendiente","confirmada","cancelada"));
        entidad("OPORTUNIDADES",3,"oportunidades",ref("prospecto_id","prospectos",false),ref("curso_id","cursos",false),opcion("etapa","nuevo","contactado","interesado","preinscrito","inscrito","perdido"));
        entidad("ESTUDIANTES",3,"estudiantes",texto("nombre",150),correo_electronico(),texto("telefono",30),ref("prospecto_id","prospectos",true));
        entidad("MATRICULAS",3,"matriculas");
        entidad("TRAMITES",4,"tramites",ref("estudiante_id","estudiantes",false),opcion("tipo","certificado","titulacion","convalidacion","otro"),opcion("estado","solicitado","en_revision","observado","aprobado","entregado"),texto("requisitos",4000),opcional("observaciones",2000));
        entidad("PERSONAL",5,"personal",ref("usuario_id","usuarios",false),texto("ci",30),texto("especialidad",200),texto("documentos",4000));
        entidad("ASIGNACIONES",5,"asignaciones",ref("instructor_id","usuarios",false),ref("grupo_id","grupos",false),decimal("tarifa_hora",0.01,10000));
        entidad("HORARIOS",6,"horarios",ref("asignacion_id","asignaciones",false),entero("dia_semana",1,7),hora("hora_inicio"),hora("hora_fin"));
        entidad("ASISTENCIAS",6,"asistencias",ref("asignacion_id","asignaciones",false),fecha("fecha"),hora("entrada"),hora("salida"),opcional("observaciones",2000));
        entidad("LIQUIDACIONES",6,"liquidaciones");
        entidad("CRITERIOS",7,"criterios",texto("nombre",150),decimal("peso",0.01,100),decimal("puntuacion_max",1,1000),bool("activo"));
        entidad("EVALUACIONES",7,"evaluaciones",ref("asignacion_id","asignaciones",false),ref("criterio_id","criterios",false),fecha("fecha"),decimal("puntuacion",0,1000),opcional("observaciones",2000));
        entidad("HISTORIAL",8,"historial");
    }

    public static Entidad obtener(String nombre) throws ErrorComando {
        Entidad e=ENTIDADES.get(nombre);
        if(e==null) throw new ErrorComando("Entidad academica desconocida. Use HELP.");
        return e;
    }

    public static boolean permitido(String rol,String nombre,boolean escribir) {
        Entidad e=ENTIDADES.get(nombre); if(e==null)return false;
        if(rol.equals("director"))return true;
        if(nombre.equals("HISTORIAL"))return false;
        if(rol.equals("secretaria"))return escribir ? List.of(2,3,4,5,6).contains(e.caso()) : e.caso()!=1;
        return rol.equals("instructor") && !escribir && List.of("CURSOS","MODULOS","GRUPOS","PERSONAL","ASIGNACIONES","HORARIOS","ASISTENCIAS","LIQUIDACIONES","CRITERIOS","EVALUACIONES").contains(nombre);
    }
}
