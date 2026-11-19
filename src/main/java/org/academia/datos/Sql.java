package org.academia.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Sql {
    private final Connection conexion;
    public Sql(Connection conexion) { this.conexion=conexion; }

    private PreparedStatement preparar(String consulta,Object... valores) throws SQLException {
        PreparedStatement s=conexion.prepareStatement(consulta);
        for(int i=0;i<valores.length;i++)s.setObject(i+1,valores[i]);
        return s;
    }
    public List<Map<String,Object>> filas(String consulta,Object... valores) throws SQLException {
        List<Map<String,Object>> datos=new ArrayList<>();
        try(var s=preparar(consulta,valores);var r=s.executeQuery()) {
            while(r.next()) {
                Map<String,Object> fila=new LinkedHashMap<>();
                for(int i=1;i<=r.getMetaData().getColumnCount();i++){
                    Object v=r.getObject(i);
                    if(v instanceof java.sql.Date || v instanceof java.sql.Time || v instanceof java.sql.Timestamp || v instanceof org.postgresql.util.PGobject)v=v.toString();
                    fila.put(r.getMetaData().getColumnLabel(i),v);
                }
                datos.add(fila);
            }
        }
        return datos;
    }
    public Map<String,Object> uno(String consulta,Object... valores) throws SQLException {
        List<Map<String,Object>> filas=filas(consulta,valores);return filas.isEmpty()?null:filas.get(0);
    }
    public long numero(String consulta,Object... valores) throws SQLException {
        Map<String,Object> fila=uno(consulta,valores);return fila==null?0:((Number)fila.values().iterator().next()).longValue();
    }
    public boolean existe(String consulta,Object... valores) throws SQLException { return uno(consulta,valores)!=null; }
    public void ejecutar(String consulta,Object... valores) throws SQLException { try(var s=preparar(consulta,valores)){s.executeUpdate();} }

    public long insertar(String tabla,Map<String,Object> datos) throws SQLException {
        String columnas=String.join(",",datos.keySet());
        String placeholders=String.join(",",java.util.Collections.nCopies(datos.size(),"?"));
        return numero("INSERT INTO "+tabla+" ("+columnas+",fecha_creacion,fecha_actualizacion) VALUES ("+placeholders+",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id",datos.values().toArray());
    }
    public void actualizar(String tabla,long id,Map<String,Object> datos) throws SQLException {
        String cambios=String.join(",",datos.keySet().stream().map(k->k+"=?").toList());
        List<Object> args=new ArrayList<>(datos.values());args.add(id);
        ejecutar("UPDATE "+tabla+" SET "+cambios+",fecha_actualizacion=CURRENT_TIMESTAMP WHERE id=?",args.toArray());
    }
}
