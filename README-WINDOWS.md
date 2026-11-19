# Windows — Academia 22SC

Requiere JDK 17+ y Maven 3.9.9; no PostgreSQL ni Docker local.
Abrir la carpeta de pom.xml en VS Code. Completar .env.remoto de forma privada.

Los comandos PowerShell completos están en README.md. Alternativa:

```powershell
powershell -File .\ejecutar-windows.ps1 --listen
```

Ctrl+C o cerrar la terminal detiene el receptor. No se instala inicio automático.
Usar una sola instancia. Al trasladarse desde Linux, detener el receptor anterior
antes de transferir .correo-procesados-22sc.log y .correo-reintentos.properties.
No usar Code Runner sobre una clase sin classpath Maven.

Los logs están en logs/access.log y logs/error.log. Leer README.md para consultar
los registros y reanudar solicitudes pausadas. El archivo privado .env.remoto
no se incluye en el ZIP de entrega.

La nueva versión no se ejecutó en Windows en esta etapa. La migración remota
sigue pendiente de acceso al servidor universitario.
