package org.academia.correo;

public final class Configuracion {
    private Configuracion() {}

    public static String valor(String nombre, String defecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    public static String requerido(String nombre) {
        String valor = valor(nombre, "");
        if (valor.isBlank()) throw new IllegalStateException("Configure " + nombre + " para " + IdentidadProyecto.NOMBRE_CORTO + ".");
        return valor;
    }
}
