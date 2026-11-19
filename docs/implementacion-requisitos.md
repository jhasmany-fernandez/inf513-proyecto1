# Estado de implementación — 19/11/2026

| Paso | Estado |
|---|---|
| Respaldo del proyecto | ZIP completo privado creado antes de editar. |
| 1. Base y relaciones | Aplicadas en db_grupo22sc con respaldo previo: 24 tablas, 29 relaciones. |
| 2. Solo asunto | Todos los comandos usan exclusivamente Subject. |
| 3. Parser | CSV unificado, corchetes/comillas, tipos, referencias, espacios y UTF-8. |
| 4. Errores y reintentos | Respuestas de validación y SQL; diferimiento persistente y pausa al límite. |
| 5. Registros | access.log y error.log rotativos, sin parámetros sensibles. |
| 6. Arranque | Manual Linux/Windows y preparación systemd/cron, sin activación automática. |
| 7. Entrega | README, arquitectura, SQL remoto actualizado y empaquetado permitido. |

## Datos históricos

Se conservaron 3 pagos pendientes de vincular y 106 solicitudes sin autor
recuperable. No se inventaron relaciones ni se borraron registros. Las nuevas
operaciones deben suministrar sus relaciones obligatorias.

## Comprobaciones realizadas

Compilación estática, revisión sintáctica de Bash, integridad del paquete y
consulta de metadatos de base. No se ejecutaron pruebas funcionales ni se inició
el receptor ni se enviaron correos durante esta implementación.

## Despliegue universitario

FTP autenticado. Puerto SSH accesible, pero autenticación rechazada para la
cuenta del grupo con la credencial disponible. Sin shell autorizado no se puede
confirmar Java ni instalar la tarea del receptor. No se subieron secretos por FTP
ni se alteraron las prácticas o aplicaciones existentes. Guía y plantillas:
despliegue/ y docs/despliegue-servidor.md.

## Publicación personal

Código publicado el 19/11/2026 en
https://github.com/jhasmany-fernandez/inf513-proyecto1 (público, rama main).
Se utilizó copia limpia del paquete de 41 archivos, sin configuración privada,
respaldos, registros ni historial antiguo. El checkout original conserva su remoto.
