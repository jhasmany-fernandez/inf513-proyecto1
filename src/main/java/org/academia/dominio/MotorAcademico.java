package org.academia.dominio;

import org.academia.datos.Sql;
import org.apache.commons.csv.CSVFormat;
import org.mindrot.jbcrypt.BCrypt;
import org.academia.correo.Configuracion;
import org.academia.pagos.PagoFacilCheckout;
import com.google.gson.Gson;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MotorAcademico {
    private final Sql db;
    private final Map<String,Object> actor;
    private final String rol;
    private static final Gson JSON=new Gson();
    private static final ZoneId ZONA=ZoneId.of("America/La_Paz");
    private static final Map<String,String> ENTIDADES_ASUNTO=Map.ofEntries(
        Map.entry("usuario","USUARIOS"),Map.entry("campana","CAMPANAS"),Map.entry("convenio","CONVENIOS"),
        Map.entry("prospecto","PROSPECTOS"),Map.entry("seguimiento","SEGUIMIENTOS"),Map.entry("curso","CURSOS"),
        Map.entry("modulo","MODULOS"),Map.entry("grupo","GRUPOS"),Map.entry("oportunidad","OPORTUNIDADES"),
        Map.entry("preinscripcion","PREINSCRIPCIONES"),Map.entry("estudiante","ESTUDIANTES"),Map.entry("matricula","MATRICULAS"),
        Map.entry("tramite","TRAMITES"),Map.entry("personal","PERSONAL"),Map.entry("asignacion","ASIGNACIONES"),
        Map.entry("horario","HORARIOS"),Map.entry("asistencia","ASISTENCIAS"),Map.entry("liquidacion","LIQUIDACIONES"),
        Map.entry("criterio","CRITERIOS"),Map.entry("evaluacion","EVALUACIONES"),Map.entry("historial","HISTORIAL"));
    private static String nombreAsunto(String entidad) {
        return ENTIDADES_ASUNTO.entrySet().stream().filter(e->e.getValue().equals(entidad)).findFirst().orElseThrow().getKey();
    }
    static String textoLimpio(String valor) {
        return valor.replaceAll("[\\p{javaWhitespace}\\p{Zs}]+", " ").trim();
    }
    private static String sinAcentos(String valor) {
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
    private static String recortar(String valor) {
        return valor.replaceAll("^[\\p{javaWhitespace}\\p{Zs}]+|[\\p{javaWhitespace}\\p{Zs}]+$", "");
    }
    public static String normalizarAsunto(String asunto) {
        int inicio=asunto.indexOf('[');
        String comando=inicio<0?asunto:asunto.substring(0,inicio);
        String palabras=sinAcentos(textoLimpio(comando)).toLowerCase(java.util.Locale.ROOT);
        String[] partes=palabras.split(" ");
        comando=palabras.toUpperCase(java.util.Locale.ROOT);
        if(palabras.equals("ayuda")||palabras.equals("help"))comando="AYUDA";
        if(partes.length==2){
            String entidad=ENTIDADES_ASUNTO.getOrDefault(partes[0],partes[0].toUpperCase(java.util.Locale.ROOT));
            String accion=switch(partes[1]){case "ayuda","help"->"AYUDA";case "mostrar","listar"->"MOSTRAR";case "agregar","crear"->"AGREGAR";case "modificar","actualizar"->"MODIFICAR";case "eliminar"->"ELIMINAR";case "ver","obtener"->"VER";default->null;};
            if(accion!=null&&Catalogo.ENTIDADES.containsKey(entidad))comando=nombreAsunto(entidad).toUpperCase(java.util.Locale.ROOT)+" "+accion;
        }
        if(inicio<0&&(comando.endsWith(" MOSTRAR")||comando.startsWith("REPORTE ")))return comando+"[*]";
        // Los parametros CSV conservan sus valores; no normalizar claves ni direcciones.
        return comando+(inicio<0?"":recortar(asunto.substring(inicio)));
    }
    static String opcionLimpia(String valor) {
        return sinAcentos(textoLimpio(valor)).replace(' ', '_').toLowerCase(java.util.Locale.ROOT);
    }
    public MotorAcademico(Sql db,Map<String,Object> actor) {this.db=db;this.actor=actor;this.rol=actor.get("rol").toString();}
    private static void exigir(boolean bien,String mensaje) throws ErrorComando {if(!bien)throw new ErrorComando(mensaje);}
    public static long id(String valor) throws ErrorComando {
        try {exigir(valor.matches("[0-9]+"),"ID debe ser entero positivo.");long n=Long.parseLong(valor);exigir(n>0,"ID debe ser entero positivo.");return n;}
        catch(NumberFormatException e){throw new ErrorComando("ID fuera de rango.");}
    }
    private static long n(Object v){return ((Number)v).longValue();}
    private static String s(Object v){return v==null?"":v.toString();}
    private static LocalDate hoy(){return LocalDate.now(ZONA);}
    private static LocalDate fecha(Object v)throws ErrorComando{
        try {String t=s(v);exigir(t.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),"Use fechas YYYY-MM-DD.");return LocalDate.parse(t);}
        catch(java.time.DateTimeException e){throw new ErrorComando("Fecha invalida.");}
    }
    private static LocalTime hora(Object v)throws ErrorComando{
        try{String t=s(v);exigir(t.matches("[0-9]{2}:[0-9]{2}"),"Use horas HH:mm.");return LocalTime.parse(t);}
        catch(java.time.DateTimeException e){throw new ErrorComando("Hora invalida.");}
    }

    public static List<String> parametros(String contenido)throws ErrorComando{
        return parametros(contenido, ',');
    }
    static List<String> parametros(String contenido,char separador)throws ErrorComando{
        try(var p=CSVFormat.DEFAULT.builder().setDelimiter(separador).setIgnoreSurroundingSpaces(true).setTrim(true).setIgnoreEmptyLines(false).get().parse(new StringReader(contenido))){
            var rows=p.getRecords();exigir(rows.size()==1,"Parametros CSV invalidos.");
            List<String> r=new ArrayList<>();rows.get(0).forEach(r::add);return r;
        }catch(java.io.IOException|java.io.UncheckedIOException|IllegalArgumentException e){throw new ErrorComando("Parametros CSV invalidos; revise comillas y comas.");}
    }
    public Map<String,Object> ejecutar(String asunto)throws Exception{
        exigir(asunto!=null && asunto.length()<=8000 && !asunto.contains("\n") && !asunto.contains("\r"),"Asunto invalido.");
        validarEstructura(asunto);
        char separador=separadorParametros(asunto);
        asunto=normalizarAsunto(asunto);if(asunto.equals("AYUDA"))return respuesta("Comandos academicos",ayuda());
        var pagoGenerar=Pattern.compile("^PAGO GENERAR\\[(.*)\\]$",Pattern.CASE_INSENSITIVE).matcher(asunto);
        if(pagoGenerar.matches()){
            exigir(rol.equals("director")||rol.equals("secretaria"),"Solo direccion y secretaria gestionan pagos.");
            var p=parametrosPago(pagoGenerar.group(1));exigir(p.size()==6,"Use pago generar [telefono;pedido;monto;detalle;matricula o tramite;id].");
            var destino=destinoPago(p.get(4),p.get(5));
            var checkout=new PagoFacilCheckout(Configuracion.requerido("PAGOFACIL_COMMERCE_ID"),Configuracion.requerido("PAGOFACIL_TOKEN_SERVICE"),Configuracion.valor("PAGOFACIL_CHECKOUT_CALLBACK_URL",""),Configuracion.valor("PAGOFACIL_CHECKOUT_RETURN_URL",""));
            BigDecimal monto;
            try { monto=new BigDecimal(p.get(2)); }
            catch(NumberFormatException e) { throw new ErrorComando("El monto debe ser numerico, positivo y tener hasta dos decimales."); }
            String formulario;
            try { formulario=checkout.formulario(actor.get("correo_electronico").toString(),p.get(0),p.get(1),monto,p.get(3)); }
            catch(IllegalArgumentException e) { throw new ErrorComando(e.getMessage()); }
            exigir(!db.existe("SELECT pedido_id FROM pagos WHERE pedido_id=?",p.get(1)),"El pedido ya existe. Use un identificador nuevo; el pago existente se conserva.");
            db.ejecutar("INSERT INTO pagos(pedido_id,correo_electronico,monto,estado,confirmacion,usuario_id,estudiante_id,matricula_id,tramite_id,pendiente_vinculacion) VALUES (?,?,?,'PENDIENTE','PENDIENTE',?,?,?,?,false)",p.get(1),actor.get("correo_electronico"),monto,actor.get("id"),destino.get("estudiante_id"),destino.get("matricula_id"),destino.get("tramite_id"));
            var r=respuesta("Formulario de Pago Fácil generado",List.of(Map.of("pedido",p.get(1),"monto_bob",new BigDecimal(p.get(2)),"formulario_html",formulario)));return r;
        }
        var vincular=Pattern.compile("^PAGO VINCULAR\\[(.*)\\]$").matcher(asunto);
        if(vincular.matches()) {
            exigir(rol.equals("director")||rol.equals("secretaria"),"Solo direccion y secretaria vinculan pagos.");
            var p=parametrosPago(vincular.group(1));exigir(p.size()==3,"Use pago vincular [pedido;matricula o tramite;id].");
            var pago=db.uno("SELECT * FROM pagos WHERE pedido_id=?",p.get(0));exigir(pago!=null,"Pedido no encontrado.");
            exigir(Boolean.TRUE.equals(pago.get("pendiente_vinculacion")),"El pago ya tiene un concepto vinculado.");
            var destino=destinoPago(p.get(1),p.get(2));
            db.ejecutar("UPDATE pagos SET estudiante_id=?,matricula_id=?,tramite_id=?,usuario_id=COALESCE(usuario_id,?),pendiente_vinculacion=false,fecha_actualizacion=CURRENT_TIMESTAMP WHERE pedido_id=?",destino.get("estudiante_id"),destino.get("matricula_id"),destino.get("tramite_id"),actor.get("id"),p.get(0));
            return respuesta("Pago vinculado; no se modifica su estado bancario",List.of(db.uno("SELECT * FROM pagos WHERE pedido_id=?",p.get(0))));
        }
        var confirmar=Pattern.compile("^PAGO CONFIRMAR\\[(.*)\\]$",Pattern.CASE_INSENSITIVE).matcher(asunto);
        if(confirmar.matches()){
            exigir(rol.equals("director"),"Solo el director puede simular la confirmacion de un pago.");
            var p=parametrosPago(confirmar.group(1));exigir(p.size()==1,"Use PAGO CONFIRMAR[pedido].");
            var pago=db.uno("SELECT * FROM pagos WHERE pedido_id=?",p.get(0));exigir(pago!=null,"Pedido de pago no encontrado.");
            exigir(!Boolean.TRUE.equals(pago.get("pendiente_vinculacion")),"Primero vincule el pago a una matricula o tramite.");
            exigir("PENDIENTE".equals(pago.get("estado")),"El pedido ya no esta pendiente; se conserva su estado actual.");
            db.ejecutar("UPDATE pagos SET estado='PAGADO',confirmacion='SIMULADA',fecha_actualizacion=CURRENT_TIMESTAMP WHERE pedido_id=?",p.get(0));
            return respuesta("Confirmacion simulada registrada",List.of(Map.of("pedido",p.get(0),"estado","PAGADO","confirmacion","SIMULADA","aviso","Simulacion academica: no acredita un pago bancario ni consulta al proveedor.")));
        }
        var ayudaEntidad=Pattern.compile("^([A-Z]+) AYUDA$").matcher(asunto);
        if(ayudaEntidad.matches()){
            String entidad=ENTIDADES_ASUNTO.get(ayudaEntidad.group(1).toLowerCase(java.util.Locale.ROOT));
            exigir(entidad!=null,"Entidad desconocida. Use ayuda.");autorizar(entidad,false);
            return respuesta("Ayuda "+nombreAsunto(entidad),ayuda().stream().filter(f->f.get("comando").toString().startsWith(nombreAsunto(entidad)+" ")).toList());
        }
        var especial=Pattern.compile("^(PREINSCRIPCION INSCRIBIR|ASIGNACION LIQUIDAR|LIQUIDACION APROBAR)\\[(.*)\\]$").matcher(asunto);
        if(especial.matches()){
            var p=parametros(especial.group(2),separador);String op=especial.group(1).split(" ")[1];
            exigir(p.size()==(op.equals("LIQUIDAR")?3:1),"Cantidad incorrecta de parametros.");
            return respuesta("Operacion completada",List.of(switch(op){case "INSCRIBIR"->inscribir(id(p.get(0)));case "LIQUIDAR"->liquidar(id(p.get(0)),fecha(p.get(1)),fecha(p.get(2)));default->aprobar(id(p.get(0)));}));
        }
        var m=Pattern.compile("^([A-Z]+) ([A-Z]+)\\[(.*)\\]$").matcher(asunto);
        exigir(m.matches(),"Comando no reconocido. Use palabras separadas, por ejemplo: usuario mostrar. Consulte ayuda.");
        String op=m.group(2),nombre=m.group(1),contenido=recortar(m.group(3));
        if(nombre.equals("REPORTE")){
            var p=contenido.equals("*")?List.<String>of():parametros(contenido,separador);exigir(p.isEmpty()||p.size()==2,"Use reporte tipo o [desde;hasta].");
            return respuesta("Reporte "+op,reportar(op,p.isEmpty()?null:fecha(p.get(0)),p.isEmpty()?null:fecha(p.get(1))));
        }
        exigir(List.of("MOSTRAR","AGREGAR","MODIFICAR","ELIMINAR","VER").contains(op),"Accion no reconocida. Use ayuda.");
        nombre=ENTIDADES_ASUNTO.get(nombre.toLowerCase(java.util.Locale.ROOT));
        exigir(nombre!=null,"Entidad desconocida. Use ayuda.");
        Catalogo.Entidad e=autorizar(nombre,op.equals("AGREGAR")||op.equals("MODIFICAR")||op.equals("ELIMINAR"));
        if(op.equals("MOSTRAR"))return listar(nombre,contenido.equals("*")?1:id(contenido));
        List<String> p=parametros(contenido,separador);
        if(op.equals("VER")||op.equals("ELIMINAR")){
            exigir(p.size()==1,"Se requiere un ID.");long registro=id(p.get(0));
            if(op.equals("VER"))return respuesta("Consulta "+nombre,List.of(obtener(nombre,registro)));
            if(nombre.equals("USUARIOS")){
                var datos=new LinkedHashMap<String,Object>(obtener(nombre,registro));datos.put("activo",false);
                return respuesta("Usuario desactivado",List.of(guardar(nombre,datos,registro)));
            }
            eliminar(nombre,registro);return respuesta("Registro eliminado",List.of());
        }
        exigir(!e.campos().isEmpty(),"Entidad de consulta; use su accion academica.");
        exigir(p.size()==e.campos().size()+(op.equals("MODIFICAR")?1:0),"Cantidad incorrecta de parametros. Use ayuda para ver su orden.");
        Long registro=op.equals("MODIFICAR")?id(p.remove(0)):null;
        Map<String,Object> datos=new LinkedHashMap<>();for(int i=0;i<p.size();i++)datos.put(e.campos().get(i).nombre(),p.get(i));
        return respuesta(registro==null?"Registro creado":"Registro actualizado",List.of(guardar(nombre,datos,registro)));
    }
    private Map<String,Object> destinoPago(String concepto,String identificador) throws Exception {
        String tipo=sinAcentos(textoLimpio(concepto)).toLowerCase(java.util.Locale.ROOT);
        exigir(tipo.equals("matricula")||tipo.equals("tramite"),"El concepto debe ser matricula o tramite.");
        long referencia=id(identificador);
        String tabla=tipo.equals("matricula")?"matriculas":"tramites";
        var registro=db.uno("SELECT estudiante_id FROM "+tabla+" WHERE id=?",referencia);
        exigir(registro!=null,"No existe la matricula o tramite indicado.");
        var resultado=new LinkedHashMap<String,Object>();
        resultado.put("estudiante_id",registro.get("estudiante_id"));
        resultado.put("matricula_id",tipo.equals("matricula")?referencia:null);
        resultado.put("tramite_id",tipo.equals("tramite")?referencia:null);
        return resultado;
    }
    private static List<String> parametrosPago(String contenido) throws ErrorComando {
        return parametros(contenido,separadorParametros("pago ["+contenido+"]"));
    }
    private static char separadorParametros(String asunto) {
        boolean comillas=false;
        for(int i=asunto.indexOf('[')+1;i<asunto.length();i++) {
            char c=asunto.charAt(i);
            if(c=='"') {
                if(comillas && i+1<asunto.length() && asunto.charAt(i+1)=='"') {i++;continue;}
                comillas=!comillas;
            }
            if(c==';'&&!comillas)return ';';
        }
        return ',';
    }
    private static void validarEstructura(String asunto) throws ErrorComando {
        int inicio=asunto.indexOf('[');
        if(inicio<0) {exigir(asunto.indexOf(']')<0,"Corchetes invalidos.");return;}
        String texto=asunto.stripTrailing();
        exigir(texto.endsWith("]"),"Cierre de parametros faltante o texto despues del corchete.");
        boolean comillas=false;
        for(int i=inicio+1;i<texto.length()-1;i++) {
            char c=texto.charAt(i);
            if(c=='"') {
                if(comillas && i+1<texto.length()-1 && texto.charAt(i+1)=='"') {i++;continue;}
                comillas=!comillas;
            } else if(!comillas) exigir(c!='['&&c!=']',"Use comillas para corchetes dentro de un parametro.");
        }
        exigir(!comillas,"Comillas sin cerrar; escape una comilla como dos comillas dobles.");
    }

    private Catalogo.Entidad autorizar(String nombre,boolean escribir)throws ErrorComando{
        Catalogo.Entidad e=Catalogo.obtener(nombre);exigir(Catalogo.permitido(rol,nombre,escribir),"Operacion no autorizada para su rol.");return e;
    }
    private String filtro(String nombre){
        if(!rol.equals("instructor"))return "";
        return switch(nombre){
            case "PERSONAL"->" AND usuario_id="+n(actor.get("id"));
            case "ASIGNACIONES"->" AND instructor_id="+n(actor.get("id"));
            case "HORARIOS","ASISTENCIAS","LIQUIDACIONES","EVALUACIONES"->" AND asignacion_id IN (SELECT id FROM asignaciones WHERE instructor_id="+n(actor.get("id"))+")";
            default->"";
        };
    }
    private static Map<String,Object> publico(Map<String,Object> fila){Map<String,Object> r=new LinkedHashMap<>(fila);r.remove("contrasena");r.remove("token_recordatorio");return r;}
    private Map<String,Object> obtener(String nombre,long id)throws Exception{
        var e=autorizar(nombre,false);var fila=db.uno("SELECT * FROM "+e.tabla()+" WHERE id=?"+filtro(nombre),id);
        exigir(fila!=null,"Registro no encontrado o fuera de su alcance.");return publico(fila);
    }
    private Map<String,Object> listar(String nombre,long pagina)throws Exception{
        var e=autorizar(nombre,false);exigir(pagina<=1000000,"Pagina fuera de rango.");
        var rows=db.filas("SELECT * FROM "+e.tabla()+" WHERE TRUE"+filtro(nombre)+" ORDER BY id DESC LIMIT 50 OFFSET ?",(pagina-1)*50).stream().map(MotorAcademico::publico).toList();
        var r=respuesta("Listado "+nombre,rows);r.put("total",db.numero("SELECT count(*) FROM "+e.tabla()+" WHERE TRUE"+filtro(nombre)));r.put("pagina",pagina);return r;
    }
    Object validarCampo(Catalogo.Campo campo,Object valor)throws Exception{
        String t=recortar(s(valor));
        if(campo.tipo()==Catalogo.Tipo.TEXTO)t=textoLimpio(t);
        if(campo.tipo()==Catalogo.Tipo.OPCION)t=opcionLimpia(t);
        if(campo.tipo()==Catalogo.Tipo.BOOLEANO)t=t.toLowerCase(java.util.Locale.ROOT);
        if(t.isEmpty()&&campo.opcional())return null;
        exigir(!t.isEmpty(),"Campo obligatorio: "+campo.nombre());
        return switch(campo.tipo()){
            case TEXTO,CONTRASENA->{exigir(t.length()>=campo.minimo()&&t.length()<=campo.maximo(),"Longitud invalida para "+campo.nombre());if(campo.tipo()==Catalogo.Tipo.CONTRASENA)exigir(t.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"La clave no debe superar 72 bytes.");yield t;}
            case CORREO_ELECTRONICO->{exigir(t.length()<=150&&!t.contains("\r")&&!t.contains("\n"),"Correo invalido.");try{var a=new jakarta.mail.internet.InternetAddress(t,true);a.validate();exigir(a.getAddress().equals(t),"Use solo la direccion de correo.");}catch(jakarta.mail.internet.AddressException x){throw new ErrorComando("Correo invalido.");}yield t.toLowerCase(java.util.Locale.ROOT);}
            case BOOLEANO->{exigir(List.of("0","1","true","false").contains(t),"Use 0 o 1 para "+campo.nombre());yield t.equals("1")||t.equals("true");}
            case ENTERO->{long v=id(t);exigir(v>=campo.minimo()&&v<=campo.maximo(),"Valor fuera de rango para "+campo.nombre());yield v;}
            case DECIMAL->{try{BigDecimal v=new BigDecimal(t);exigir(v.compareTo(BigDecimal.valueOf(campo.minimo()))>=0&&v.compareTo(BigDecimal.valueOf(campo.maximo()))<=0&&v.stripTrailingZeros().scale()<=2,"Decimal fuera de rango; use hasta dos decimales para "+campo.nombre());yield v;}catch(NumberFormatException x){throw new ErrorComando("Numero invalido: "+campo.nombre());}}
            case FECHA->fecha(t);
            case HORA->hora(t);
            case OPCION->{exigir(campo.opciones().contains(t),"Valor no permitido para "+campo.nombre()+": "+String.join(",",campo.opciones()));yield t;}
            case REFERENCIA->{long v=id(t);exigir(db.existe("SELECT id FROM "+campo.tabla()+" WHERE id=?",v),"Referencia inexistente: "+campo.nombre());yield v;}
        };
    }
    private Map<String,Object> guardar(String nombre,Map<String,Object> entrada,Long registro)throws Exception{
        var e=autorizar(nombre,true);Map<String,Object> antes=registro==null?null:obtener(nombre,registro);
        Map<String,Object> datos=new LinkedHashMap<>();
        for(var campo:e.campos()){
            Object valor=entrada.containsKey(campo.nombre())?entrada.get(campo.nombre()):antes==null?null:antes.get(campo.nombre());
            if(campo.nombre().equals("contrasena")&&registro!=null&&s(valor).isBlank())continue;
            datos.put(campo.nombre(),validarCampo(campo,valor));
        }
        validarNegocio(nombre,datos,antes,registro);
        if(datos.containsKey("contrasena"))datos.put("contrasena",BCrypt.hashpw(s(datos.get("contrasena")),BCrypt.gensalt()));
        long id=registro==null?db.insertar(e.tabla(),datos):registro;
        if(registro!=null)db.actualizar(e.tabla(),registro,datos);
        Map<String,Object> despues=obtener(nombre,id);historial(nombre,id,registro==null?"registrar":"actualizar",antes,despues);return despues;
    }
    private void historial(String nombre,long id,String accion,Map<String,Object> antes,Map<String,Object> despues)throws Exception{
        db.ejecutar("INSERT INTO historial (usuario_id,entidad,registro_id,accion,antes,despues,fecha_creacion,fecha_actualizacion) VALUES (?,?,?,?,?::json,?::json,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            actor.get("id"),nombre,id,accion,antes==null?null:JSON.toJson(publico(antes)),despues==null?null:JSON.toJson(publico(despues)));
    }
    public static Map<String,Object> respuesta(String mensaje,List<?> datos){Map<String,Object> r=new LinkedHashMap<>();r.put("mensaje",mensaje);r.put("datos",datos);return r;}

    private void validarNegocio(String nombre,Map<String,Object> d,Map<String,Object> antes,Long id)throws Exception{
        if(d.containsKey("inicio")&&d.containsKey("fin"))exigir(!((LocalDate)d.get("fin")).isBefore((LocalDate)d.get("inicio")),"Fin no puede ser anterior al inicio.");
        if(nombre.equals("USUARIOS")&&antes!=null){
            if(s(antes.get("rol")).equals("director")&&Boolean.TRUE.equals(antes.get("activo"))&&(!s(d.get("rol")).equals("director")||!Boolean.TRUE.equals(d.get("activo"))))
                exigir(db.existe("SELECT id FROM usuarios WHERE rol='director' AND activo AND id<>?",id),"Debe permanecer un director activo.");
            if(!s(d.get("rol")).equals("instructor")||!Boolean.TRUE.equals(d.get("activo")))exigir(!db.existe("SELECT id FROM asignaciones WHERE instructor_id=?",id),"El instructor tiene asignaciones; conserve su rol y estado.");
        }
        if(nombre.equals("CURSOS")&&id!=null&&!Boolean.TRUE.equals(d.get("activo")))exigir(!db.existe("SELECT p.id FROM preinscripciones p JOIN grupos g ON g.id=p.grupo_id JOIN modulos m ON m.id=g.modulo_id WHERE m.curso_id=? AND p.estado<>'cancelada'",id),"El curso tiene preinscripciones vigentes.");
        if(nombre.equals("MODULOS")&&antes!=null&&db.existe("SELECT id FROM grupos WHERE modulo_id=?",id))exigir(n(d.get("curso_id"))==n(antes.get("curso_id")),"El modulo con grupos conserva su curso.");
        if(nombre.equals("GRUPOS")&&antes!=null){
            exigir(n(d.get("cupo"))>=db.numero("SELECT count(*) FROM preinscripciones WHERE grupo_id=? AND estado<>'cancelada'",id),"Cupo menor que las reservas vigentes.");
            if(db.existe("SELECT id FROM asignaciones WHERE grupo_id=?",id))exigir(n(d.get("modulo_id"))==n(antes.get("modulo_id"))&&s(d.get("inicio")).equals(s(antes.get("inicio")))&&s(d.get("fin")).equals(s(antes.get("fin"))),"El grupo asignado conserva modulo y periodo.");
            if(db.existe("SELECT id FROM preinscripciones WHERE grupo_id=?",id))exigir(n(d.get("modulo_id"))==n(antes.get("modulo_id")),"Un grupo con preinscripciones conserva su modulo.");
        }
        if(nombre.equals("ESTUDIANTES")&&antes!=null&&db.existe("SELECT id FROM matriculas WHERE estudiante_id=?",id))exigir(java.util.Objects.equals(d.get("prospecto_id"),antes.get("prospecto_id")),"El estudiante matriculado conserva su prospecto.");
        if(nombre.equals("PERSONAL")&&antes!=null&&db.existe("SELECT id FROM asignaciones WHERE instructor_id=?",antes.get("usuario_id")))exigir(n(d.get("usuario_id"))==n(antes.get("usuario_id")),"El expediente asignado conserva su usuario.");
        if(nombre.equals("PREINSCRIPCIONES")){
            if(id!=null)exigir(!db.existe("SELECT id FROM matriculas WHERE preinscripcion_id=?",id),"La preinscripcion ya fue matriculada.");
            var g=db.uno("SELECT g.*,c.activo FROM grupos g JOIN modulos m ON m.id=g.modulo_id JOIN cursos c ON c.id=m.curso_id WHERE g.id=?",d.get("grupo_id"));
            exigir(Boolean.TRUE.equals(g.get("activo")),"El curso no esta activo.");
            exigir(!db.existe("SELECT id FROM preinscripciones WHERE prospecto_id=? AND grupo_id=? AND id<>?",d.get("prospecto_id"),d.get("grupo_id"),id==null?0:id),"Ya existe esta preinscripcion.");
            if(!s(d.get("estado")).equals("cancelada"))exigir(db.numero("SELECT count(*) FROM preinscripciones WHERE grupo_id=? AND estado<>'cancelada' AND id<>?",d.get("grupo_id"),id==null?0:id)<n(g.get("cupo")),"No hay cupos disponibles.");
        }
        if(nombre.equals("OPORTUNIDADES")){
            exigir(!db.existe("SELECT id FROM oportunidades WHERE prospecto_id=? AND curso_id=? AND id<>?",d.get("prospecto_id"),d.get("curso_id"),id==null?0:id),"Ya existe esta oportunidad.");
            if(antes==null)exigir(s(d.get("etapa")).equals("nuevo"),"La oportunidad comienza en nuevo.");
            else{
                exigir(n(d.get("prospecto_id"))==n(antes.get("prospecto_id"))&&n(d.get("curso_id"))==n(antes.get("curso_id")),"La oportunidad conserva prospecto y curso.");
                transicion(s(antes.get("etapa")),s(d.get("etapa")),Map.of("nuevo",List.of("contactado","perdido"),"contactado",List.of("interesado","perdido"),"interesado",List.of("preinscrito","perdido"),"preinscrito",List.of("inscrito","perdido"),"inscrito",List.of(),"perdido",List.of("nuevo")));
            }
            if(List.of("preinscrito","inscrito").contains(s(d.get("etapa"))))exigir(db.existe("SELECT p.id FROM preinscripciones p JOIN grupos g ON g.id=p.grupo_id JOIN modulos m ON m.id=g.modulo_id "+(s(d.get("etapa")).equals("inscrito")?"JOIN matriculas x ON x.preinscripcion_id=p.id ":"")+"WHERE p.prospecto_id=? AND m.curso_id=? AND p.estado<>'cancelada'",d.get("prospecto_id"),d.get("curso_id")),"Falta preinscripcion o matricula para esta etapa.");
        }
        if(nombre.equals("TRAMITES")){
            if(antes==null)exigir(s(d.get("estado")).equals("solicitado"),"El tramite comienza en solicitado.");
            else{
                exigir(n(d.get("estudiante_id"))==n(antes.get("estudiante_id"))&&s(d.get("tipo")).equals(s(antes.get("tipo"))),"El tramite conserva estudiante y tipo.");
                transicion(s(antes.get("estado")),s(d.get("estado")),Map.of("solicitado",List.of("en_revision"),"en_revision",List.of("observado","aprobado"),"observado",List.of("en_revision"),"aprobado",List.of("entregado"),"entregado",List.of()));
            }
            if(s(d.get("estado")).equals("observado"))exigir(!s(d.get("observaciones")).isBlank(),"Detalle los requisitos pendientes.");
            if(s(d.get("tipo")).equals("titulacion"))exigir(db.existe("SELECT id FROM matriculas WHERE estudiante_id=?",d.get("estudiante_id")),"Titulacion requiere estudiante matriculado.");
        }
        if(nombre.equals("ASIGNACIONES")){
            exigir(db.existe("SELECT id FROM usuarios WHERE id=? AND activo AND rol='instructor'",d.get("instructor_id")),"Seleccione un instructor activo.");
            exigir(db.existe("SELECT id FROM personal WHERE usuario_id=?",d.get("instructor_id")),"Registre primero el expediente del instructor.");
            if(antes!=null)exigir(n(d.get("instructor_id"))==n(antes.get("instructor_id"))&&n(d.get("grupo_id"))==n(antes.get("grupo_id")),"La asignacion conserva instructor y grupo.");
        }
        if(nombre.equals("HORARIOS"))validarHorario(d,antes,id);
        if(nombre.equals("ASISTENCIAS"))validarAsistencia(d,antes,id);
        if(nombre.equals("EVALUACIONES")){
            var criterio=db.uno("SELECT * FROM criterios WHERE id=?",d.get("criterio_id"));
            exigir(Boolean.TRUE.equals(criterio.get("activo"))&&((BigDecimal)d.get("puntuacion")).compareTo((BigDecimal)criterio.get("puntuacion_max"))<=0,"Criterio inactivo o puntuacion fuera de escala.");
            exigir(!((LocalDate)d.get("fecha")).isAfter(hoy()),"No registre evaluaciones futuras.");
            d.put("evaluador_id",actor.get("id"));
        }
        if(nombre.equals("CRITERIOS")&&antes!=null&&db.existe("SELECT id FROM evaluaciones WHERE criterio_id=?",id)){
            exigir(((BigDecimal)d.get("peso")).compareTo((BigDecimal)antes.get("peso"))==0
                &&((BigDecimal)d.get("puntuacion_max")).compareTo((BigDecimal)antes.get("puntuacion_max"))==0,
                "El criterio evaluado conserva peso y escala; cree otro criterio.");
        }
    }
    private static void transicion(String antes,String despues,Map<String,List<String>> pasos)throws ErrorComando{
        exigir(antes.equals(despues)||pasos.getOrDefault(antes,List.of()).contains(despues),"Transicion de estado no permitida.");
    }
    private void validarHorario(Map<String,Object>d,Map<String,Object>antes,Long id)throws Exception{
        LocalTime inicio=(LocalTime)d.get("hora_inicio"),fin=(LocalTime)d.get("hora_fin");exigir(fin.isAfter(inicio),"Fin debe ser posterior a inicio.");
        if(antes!=null)exigir(!db.existe("SELECT id FROM asistencias WHERE asignacion_id=?",antes.get("asignacion_id")),"El horario ya tiene asistencias.");
        var a=db.uno("SELECT a.*,g.inicio,g.fin FROM asignaciones a JOIN grupos g ON g.id=a.grupo_id WHERE a.id=?",d.get("asignacion_id"));
        exigir(!db.existe("SELECT h.id FROM horarios h JOIN asignaciones a ON a.id=h.asignacion_id JOIN grupos g ON g.id=a.grupo_id WHERE a.instructor_id=? AND h.dia_semana=? AND g.inicio<=?::date AND g.fin>=?::date AND h.hora_inicio<? AND h.hora_fin>? AND h.id<>?",a.get("instructor_id"),d.get("dia_semana"),a.get("fin"),a.get("inicio"),fin,inicio,id==null?0:id),"El instructor tiene un cruce horario.");
    }
    private void validarAsistencia(Map<String,Object>d,Map<String,Object>antes,Long id)throws Exception{
        exigir(antes==null||antes.get("liquidacion_id")==null,"Una asistencia liquidada no puede modificarse.");
        LocalDate f=(LocalDate)d.get("fecha");LocalTime entrada=(LocalTime)d.get("entrada"),salida=(LocalTime)d.get("salida");exigir(salida.isAfter(entrada),"Salida debe ser posterior a entrada.");
        var a=db.uno("SELECT a.*,g.inicio,g.fin FROM asignaciones a JOIN grupos g ON g.id=a.grupo_id WHERE a.id=?",d.get("asignacion_id"));
        exigir(!f.isBefore(fecha(a.get("inicio")))&&!f.isAfter(fecha(a.get("fin")))&&!f.isAfter(hoy()),"Fecha fuera del periodo del grupo o futura.");
        exigir(!db.existe("SELECT id FROM liquidaciones WHERE asignacion_id=? AND desde<=? AND hasta>=?",a.get("id"),f,f),"El periodo ya fue liquidado.");
        var horarios=db.filas("SELECT * FROM horarios WHERE asignacion_id=? AND dia_semana=? AND hora_inicio<? AND hora_fin>?",a.get("id"),f.getDayOfWeek().getValue(),salida,entrada);
        exigir(horarios.size()==1,"La asistencia debe corresponder a un unico bloque horario.");
        exigir(!db.existe("SELECT id FROM asistencias WHERE asignacion_id=? AND fecha=? AND entrada<? AND salida>? AND id<>?",a.get("id"),f,salida,entrada,id==null?0:id),"Ya existe asistencia en este intervalo.");
        var h=horarios.get(0);LocalTime hi=LocalTime.parse(s(h.get("hora_inicio"))),hf=LocalTime.parse(s(h.get("hora_fin")));
        LocalTime desde=entrada.isAfter(hi)?entrada:hi,hasta=salida.isBefore(hf)?salida:hf;
        d.put("minutos",java.time.Duration.between(desde,hasta).toMinutes());d.put("minutos_programados",java.time.Duration.between(hi,hf).toMinutes());d.put("cumple_horario",!entrada.isAfter(hi)&&!salida.isBefore(hf));
    }
    private void eliminar(String nombre,long id)throws Exception{
        var e=autorizar(nombre,true);exigir(!e.campos().isEmpty()&&!nombre.equals("USUARIOS"),"Use desactivacion para usuarios; registros generados son de consulta.");
        var antes=obtener(nombre,id);
        if(nombre.equals("ASISTENCIAS"))exigir(antes.get("liquidacion_id")==null,"Una asistencia liquidada no puede eliminarse.");
        if(nombre.equals("HORARIOS"))exigir(!db.existe("SELECT id FROM asistencias WHERE asignacion_id=?",antes.get("asignacion_id")),"El horario tiene asistencias.");
        if(nombre.equals("TRAMITES"))exigir(s(antes.get("estado")).equals("solicitado"),"El tramite ya fue procesado.");
        db.ejecutar("DELETE FROM "+e.tabla()+" WHERE id=?",id);historial(nombre,id,"eliminar",antes,null);
    }
    private Map<String,Object> inscribir(long id)throws Exception{
        autorizar("PREINSCRIPCIONES",true);var p=obtener("PREINSCRIPCIONES",id);
        exigir(s(p.get("estado")).equals("confirmada"),"Confirme primero la preinscripcion.");
        exigir(!db.existe("SELECT id FROM matriculas WHERE preinscripcion_id=?",id),"La preinscripcion ya fue matriculada.");
        var g=db.uno("SELECT * FROM grupos WHERE id=?",p.get("grupo_id"));exigir(db.numero("SELECT count(*) FROM matriculas m JOIN preinscripciones p ON p.id=m.preinscripcion_id WHERE p.grupo_id=?",g.get("id"))<n(g.get("cupo")),"No hay cupos.");
        var prospecto=db.uno("SELECT * FROM prospectos WHERE id=?",p.get("prospecto_id"));var estudiante=db.uno("SELECT * FROM estudiantes WHERE prospecto_id=?",prospecto.get("id"));
        if(estudiante==null){
            Map<String,Object>d=new LinkedHashMap<>();for(String k:List.of("nombre","correo_electronico","telefono"))d.put(k,prospecto.get(k));d.put("prospecto_id",prospecto.get("id"));estudiante=guardar("ESTUDIANTES",d,null);
        }
        exigir(!db.existe("SELECT m.id FROM matriculas m JOIN preinscripciones p ON p.id=m.preinscripcion_id WHERE m.estudiante_id=? AND p.grupo_id=?",estudiante.get("id"),g.get("id")),"El estudiante ya esta matriculado en este grupo.");
        long mid=db.insertar("matriculas",Map.of("estudiante_id",estudiante.get("id"),"preinscripcion_id",id));
        var resultado=obtener("MATRICULAS",mid);historial("MATRICULAS",mid,"inscribir",null,resultado);
        var o=db.uno("SELECT o.* FROM oportunidades o JOIN modulos m ON m.curso_id=o.curso_id WHERE m.id=? AND o.prospecto_id=?",g.get("modulo_id"),prospecto.get("id"));
        if(o!=null){db.actualizar("oportunidades",n(o.get("id")),Map.of("etapa","inscrito"));historial("OPORTUNIDADES",n(o.get("id")),"inscribir",o,obtener("OPORTUNIDADES",n(o.get("id"))));}
        return resultado;
    }
    private Map<String,Object> liquidar(long asignacion,LocalDate desde,LocalDate hasta)throws Exception{
        autorizar("LIQUIDACIONES",true);exigir(!hasta.isBefore(desde)&&!hasta.isAfter(hoy()),"Periodo invalido o futuro.");var a=obtener("ASIGNACIONES",asignacion);
        exigir(!db.existe("SELECT id FROM liquidaciones WHERE asignacion_id=? AND desde<=? AND hasta>=?",asignacion,hasta,desde),"Existe liquidacion que cruza este periodo.");
        long minutos=db.numero("SELECT COALESCE(SUM(minutos),0) FROM asistencias WHERE asignacion_id=? AND fecha BETWEEN ? AND ? AND liquidacion_id IS NULL",asignacion,desde,hasta);exigir(minutos>0,"No hay horas cumplidas sin liquidar.");
        BigDecimal tarifa=(BigDecimal)a.get("tarifa_hora"),horas=BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
        BigDecimal total=BigDecimal.valueOf(minutos).multiply(tarifa).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP);
        long lid=db.insertar("liquidaciones",Map.of("asignacion_id",asignacion,"desde",desde,"hasta",hasta,"horas",horas,"tarifa_hora",tarifa,"total",total,"estado","pendiente"));
        db.ejecutar("UPDATE asistencias SET liquidacion_id=?,fecha_actualizacion=CURRENT_TIMESTAMP WHERE asignacion_id=? AND fecha BETWEEN ? AND ? AND liquidacion_id IS NULL",lid,asignacion,desde,hasta);
        var resultado=obtener("LIQUIDACIONES",lid);historial("LIQUIDACIONES",lid,"liquidar",null,resultado);return resultado;
    }
    private Map<String,Object> aprobar(long id)throws Exception{
        exigir(rol.equals("director"),"Solo el director aprueba honorarios.");var antes=obtener("LIQUIDACIONES",id);exigir(s(antes.get("estado")).equals("pendiente"),"La liquidacion ya fue aprobada.");
        db.actualizar("liquidaciones",id,Map.of("estado","aprobada"));var resultado=obtener("LIQUIDACIONES",id);historial("LIQUIDACIONES",id,"aprobar",antes,resultado);return resultado;
    }
    private List<?> reportar(String tipo,LocalDate desde,LocalDate hasta)throws Exception{
        exigir(List.of("director","secretaria").contains(rol),"Reporte administrativo no autorizado.");exigir(Catalogo.REPORTES.contains(tipo),"Reporte desconocido.");exigir(desde==null||!hasta.isBefore(desde),"Periodo invalido.");
        if(tipo.equals("GENERAL")){
            List<Map<String,Object>> rows=new ArrayList<>();for(String tabla:List.of("usuarios","prospectos","cursos","preinscripciones","estudiantes","tramites","asignaciones","asistencias","liquidaciones","evaluaciones"))rows.add(Map.of("indicador",tabla,"cantidad",desde==null?db.numero("SELECT count(*) FROM "+tabla):db.numero("SELECT count(*) FROM "+tabla+" WHERE fecha_creacion::date BETWEEN ? AND ?",desde,hasta)));return rows;
        }
        String select,from,grupo,campo;
        switch(tipo){
            case "CAPTACION"->{select="p.canal,COALESCE(c.nombre,'Sin campana') AS campana,COALESCE(v.nombre,'Sin convenio') AS convenio,COUNT(*) AS cantidad";from="prospectos p LEFT JOIN campanas c ON c.id=p.campana_id LEFT JOIN convenios v ON v.id=p.convenio_id";grupo="p.canal,c.nombre,v.nombre";campo="p.fecha_creacion";}
            case "CRM"->{select="etapa,COUNT(*) AS cantidad";from="oportunidades";grupo="etapa";campo="fecha_creacion";}
            case "TRAMITES"->{select="tipo,estado,COUNT(*) AS cantidad";from="tramites";grupo="tipo,estado";campo="fecha_creacion";}
            case "ASISTENCIA"->{select="u.id AS instructor_id,u.nombre,SUM(minutos) AS minutos_cumplidos,SUM(minutos_programados) AS minutos_programados,COUNT(*) AS sesiones,SUM(CASE WHEN cumple_horario THEN 1 ELSE 0 END) AS sesiones_puntuales";from="asistencias x JOIN asignaciones a ON a.id=x.asignacion_id JOIN usuarios u ON u.id=a.instructor_id";grupo="u.id,u.nombre";campo="fecha";}
            case "HONORARIOS"->{select="u.id AS instructor_id,u.nombre,x.estado,SUM(horas) AS horas,SUM(total) AS total";from="liquidaciones x JOIN asignaciones a ON a.id=x.asignacion_id JOIN usuarios u ON u.id=a.instructor_id";grupo="u.id,u.nombre,x.estado";campo="x.fecha_creacion";}
            default->{select="u.id AS instructor_id,u.nombre,m.nombre AS modulo,COUNT(*) AS respuestas,ROUND(SUM(x.puntuacion/c.puntuacion_max*c.peso)/NULLIF(SUM(c.peso),0)*100,2) AS calidad_porcentaje";from="evaluaciones x JOIN criterios c ON c.id=x.criterio_id JOIN asignaciones a ON a.id=x.asignacion_id JOIN usuarios u ON u.id=a.instructor_id JOIN grupos g ON g.id=a.grupo_id JOIN modulos m ON m.id=g.modulo_id";grupo="u.id,u.nombre,m.nombre";campo="fecha";}
        }
        String q="SELECT "+select+" FROM "+from+(desde==null?"":" WHERE "+campo+"::date BETWEEN ? AND ?")+" GROUP BY "+grupo+" ORDER BY 1";
        return desde==null?db.filas(q):db.filas(q,desde,hasta);
    }
    private List<Map<String,Object>> ayuda(){
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var item:Catalogo.ENTIDADES.entrySet()){
            String nombre=item.getKey(), etiqueta=nombreAsunto(nombre);var e=item.getValue();if(!Catalogo.permitido(rol,nombre,false))continue;
            rows.add(Map.of("caso","CU"+e.caso(),"comando",etiqueta+" ayuda / "+etiqueta+" mostrar / "+etiqueta+" ver [id]","parametros","Pagina opcional: "+etiqueta+" mostrar [2]"));
            if(!e.campos().isEmpty()&&Catalogo.permitido(rol,nombre,true)){
                String campos=String.join("; ",e.campos().stream().map(Catalogo.Campo::nombre).toList());
                rows.add(Map.of("caso","CU"+e.caso(),"comando",etiqueta+" agregar ["+campos+"]","parametros",campos));
                rows.add(Map.of("caso","CU"+e.caso(),"comando",etiqueta+" modificar [id; "+campos+"]","parametros",nombre.equals("USUARIOS")?"Todos los campos en orden; clave vacia conserva la actual.":"Todos los campos en orden, incluidos los opcionales vacios."));
                rows.add(Map.of("caso","CU"+e.caso(),"comando",etiqueta+" eliminar [id]","parametros",nombre.equals("USUARIOS")?"Baja logica: desactiva sin borrar; conserva un director activo.":"Solo registros sin dependencias."));
            }
        }
        if(List.of("director","secretaria").contains(rol)){
            rows.add(Map.of("caso","CU3","comando","pago generar [telefono;pedido;monto;detalle;matricula o tramite;id]","parametros","Concepto obligatorio. El estudiante se obtiene de la matricula o tramite."));
            rows.add(Map.of("caso","CU3","comando","pago vincular [pedido;matricula o tramite;id]","parametros","Completar concepto de un pago historico sin alterar su estado."));
            rows.add(Map.of("caso","CU3","comando","preinscripcion inscribir [preinscripcion_id]","parametros","Preinscripcion confirmada."));
            rows.add(Map.of("caso","CU6","comando","asignacion liquidar [asignacion_id; desde; hasta]","parametros","Fechas YYYY-MM-DD."));
            if(rol.equals("director"))rows.add(Map.of("caso","CU6","comando","liquidacion aprobar [liquidacion_id]","parametros","Solo director."));
            for(String tipo:Catalogo.REPORTES){
                String comando="reporte "+tipo.toLowerCase(java.util.Locale.ROOT);
                rows.add(Map.of("caso","CU8","comando",comando,"parametros",
                    "Fechas opcionales (YYYY-MM-DD). Sin fechas consulta todo el historial. Ejemplo: "+comando+" [2026-10-01; 2026-10-31]"));
            }
        }
        rows.sort(java.util.Comparator.comparingInt(fila->Integer.parseInt(fila.get("caso").toString().substring(2))));
        return rows;
    }
}
