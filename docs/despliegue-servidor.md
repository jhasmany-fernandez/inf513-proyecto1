# Despliegue del receptor universitario

Estado: preparación local. No se ha instalado ni ejecutado en el servidor.
La disponibilidad de SSH, Java, tareas programadas y systemd de usuario debe
confirmarse con la universidad. FTP solo transfiere archivos: no ejecuta Java.

## Ubicación y requisitos

Usar una carpeta privada del usuario asignado, fuera del directorio público web.
El servidor necesita Java 17+, Bash y flock. Maven solo es necesario si compila
allí. Estos archivos no instalan paquetes ni suponen permisos administrativos.
La base y correo siguen usando las credenciales reales de 22SC.

## Preparación

1. Detener receptores de Windows y Linux que utilicen el mismo buzón.
2. Respaldar la base antes de migrar. La migración relacional se aplicó el 19/11/2026 con respaldo previo.
   Conservar ese respaldo y no ejecutar versiones anteriores.
3. Compilar el código sin pruebas funcionales:

```bash
bash mvn-local.sh -Dmaven.test.skip=true compile dependency:copy-dependencies -DincludeScope=runtime
```

4. Transferir fuentes, despliegue/, target/classes y target/dependency. El ZIP
   académico de entrega no incluye target: debe compilarse o transferirse aparte.
5. Transferir privadamente .env.remoto y el estado más reciente del receptor:
   .correo-procesados-22sc.log y .correo-reintentos.properties, si existen.
   Nunca sobrescribir un estado más nuevo con uno antiguo.
6. En la carpeta del servidor, restringir la configuración:

```bash
chmod 600 .env.remoto
```

7. Elegir UN mecanismo de inicio. Todo arranque inicializa la base y puede
   aplicar migraciones; revisar respaldo y versión antes de iniciarlo.

## Opción A: inicio manual

```bash
bash despliegue/receptor-servidor.sh --listen
```

Ctrl+C lo detiene. El lanzador conserva un bloqueo exclusivo mientras el proceso
vive; solo impide duplicados en esta carpeta y con este mismo lanzador. No
coordina dos computadoras distintas ni procesos Java iniciados directamente.

## Opción B: servicio de usuario

Si el servidor permite systemd de usuario, copiar la plantilla y sustituir
/RUTA/ABSOLUTA por la ruta real. El nombre del archivo instalado debe ser
correo-academia.service. Luego, de forma explícita:

```bash
systemctl --user daemon-reload
systemctl --user enable --now correo-academia
systemctl --user status correo-academia
journalctl --user -u correo-academia -f
```

La permanencia después de cerrar sesión depende de la política del servidor;
consultar al administrador. Para detener y quitar el inicio automático:

```bash
systemctl --user disable --now correo-academia
```

## Opción C: cron

Si se permite cron pero no un servicio persistente, programar revisión de una
sola pasada. Ejemplo orientativo, sustituyendo la ruta (las comillas conservan
los espacios). No copiar sin adaptar:

```cron
* * * * * /bin/bash "/RUTA/ABSOLUTA/correo_academia/despliegue/receptor-servidor.sh" --once >> "/RUTA/ABSOLUTA/correo_academia/logs/cron.log" 2>&1
```

Crear logs/ antes de activar cron. Configurar PATH con Java accesible en crontab.
El bloqueo evita solapamientos si un ciclo tarda más de un minuto. Quitar esa
línea para detener nuevos ciclos; no activar cron junto al servicio continuo.

## Registros y recuperación

Consultar logs/access.log, logs/error.log y el journal si se usa systemd.
Las solicitudes con reintentos agotados quedan pausadas sin borrarse. Después
de corregir la causa, detener el receptor y retirar únicamente su entrada hash
del archivo de reintentos. Ver README.md para los detalles.

## Pendientes externos

- Respaldo y migración remota completados el 19/11/2026.
- Confirmar ruta, permisos y mecanismo de ejecución universitario.
- Publicar el código en el repositorio personal exigido por la asignatura.
- Verificar despliegue cuando el usuario autorice pruebas de funcionamiento.

## Acceso comprobado — 19/11/2026

FTP autentica con la cuenta del grupo. SSH devuelve Permission denied con la
credencial disponible; no se puede ejecutar ni instalar el daemon por esa vía.
No se subieron archivos de configuración privada ni se iniciaron procesos.
Se necesita habilitación de shell para la cuenta o un mecanismo de ejecución
aprobado por el administrador. La base ya fue migrada y respaldada.
