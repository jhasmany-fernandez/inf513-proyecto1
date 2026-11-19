# Modelo de pagos y solicitudes

- `usuarios` → `solicitudes_correo`: autor de cada solicitud nueva.
- `usuarios` → `pagos`: responsable del registro o de la vinculación histórica.
- `estudiantes` → `pagos`: beneficiario académico del pago.
- `matriculas` → `pagos`: concepto de inscripción.
- `tramites` → `pagos`: concepto de trámite.

Cada pago nuevo requiere exactamente una matrícula o un trámite. Las claves
foráneas compuestas garantizan que ambos registros correspondan al mismo
estudiante; no se admite simultáneamente matrícula y trámite.

Se renombra pagos_checkout a pagos y correo a correo_electronico, preservando
sus registros. Los antiguos sin concepto conservan pendiente_vinculacion=true
hasta completar la relación con `pago vincular`. No se asigna un estudiante por
coincidencia de correo ni se convierte un pago simulado en un pago bancario.

Las solicitudes históricas guardaban un hash que no permite recuperar su autor
por sí solo. Se conservan con autoria_pendiente=true, sin inventar usuarios.
Las solicitudes nuevas requieren usuario_id; un CHECK distingue las históricas.
La tabla técnica migraciones sigue independiente.

La migración es idempotente y transaccional. No borra tablas ni datos del negocio.
Se preparó el código Java, el SQL y la ayuda del correo. La migración se aplicó en db_grupo22sc el 19/11/2026, con respaldo previo. No se hicieron más
pruebas después de la instrucción del usuario de suspenderlas.
