# Pago Fácil Checkout

## Actualización del modelo relacional (18/11/2026)

La versión actual usa `pagos` y `correo_electronico` en lugar de
`pagos_checkout` y `correo`. Los ejemplos antiguos de cuatro parámetros que
aparecen más abajo describen la versión anterior. Ahora el asunto debe incluir
su concepto y referencia:

```text
pago generar [70000000;ACA-001;0.01;Inscripción;matricula;1]
pago generar [70000000;ACA-002;0.01;Certificado;tramite;1]
pago vincular [PEDIDO-HISTORICO;matricula;1]
```

Director y secretaría pueden generar y vincular. Los IDs son ejemplos y deben
corresponder a registros existentes. Se obtiene el estudiante de la matrícula
o trámite; PostgreSQL verifica la coincidencia mediante claves compuestas y
rechaza un pago con ambos conceptos. Los duplicados no se sobrescriben.

Los pagos históricos quedan pendientes de vincular si no contienen concepto.
La migración no inventa referencias ni cambia su importe o estado.
Las solicitudes nuevas se relacionan con el usuario remitente. Las históricas
sin autor recuperable se marcan con `autoria_pendiente`; repetir un Message-ID
conocido permite recuperar el usuario y conservar la respuesta anterior.

`pago confirmar [pedido]` sigue siendo una simulación exclusiva del director y
exige un pago pendiente ya vinculado. No acredita un pago bancario.

Migración: `src/main/resources/relaciones_correo.sql`, ejecutada por BaseDatos
en su transacción de inicio. También se entrega una copia transaccional en
`docs/aplicar-relaciones-correo.sql`. Respaldar la base y detener receptores
antiguos antes de aplicarla; las versiones previas usan el nombre viejo.

Estado: implementación preparada. Su aplicación en db_grupo22sc está pendiente
porque tecnoweb.org.bo no resuelve por DNS. La última exportación remota se
conserva en db_grupo22sc_estructura.sql. No es el esquema nuevo desplegado.

## Referencia del flujo anterior

Las credenciales entregadas corresponden a Checkout, no al API MasterQR. El
flujo correcto no usa `/login`: genera `tcDatosCheckout` con la cadena
`TokenService|Email|Telefono|PedidoID|Monto|2|Callback|Retorno|Detalle|11`,
la codifica en Base64 y envía un formulario `POST` a
`https://checkout.pagofacil.com.bo/es/pay` junto con `tcCommerceID`.

La clase `org.academia.pagos.PagoFacilCheckout` genera el formulario y usa
`2` para bolivianos. El monto de prueba puede ser `0.01`.

Desde el correo se usa `PAGO GENERAR[telefono;pedido;monto;detalle]`. Ejemplo:

`PAGO GENERAR[70000000;ACA-001;0.01;Inscripcion de prueba]`

El comando completo puede ir en el asunto, con palabras separadas y sin
distinción de mayúsculas: `pago generar [70000000;ACA-001;0.01;Inscripción]`.
Cada pedido debe tener un identificador nuevo. Generar un ID ya registrado
devuelve un error y conserva su importe y estado. Los montos inválidos generan
una respuesta de validación y no se reintentan como fallos de infraestructura.

`pago confirmar [pedido]` es una simulación académica exclusiva del director,
solo para pedidos pendientes. La respuesta indica expresamente que no acredita
un pago bancario. No sustituye un callback verificado del proveedor.

El 10/11/2026 se comprobó con Chrome que el botón del correo abre Checkout,
muestra «Demo Integracion» y Bs 0,01, y permite acceder a la opción QR BCP.
El proveedor solicita datos del titular y entidad financiera antes de generar
el pago. No se efectuó un pago ni se verificó confirmación bancaria.

## Despliegue confirmado — 19/11/2026

La migración de relaciones ya se aplicó en db_grupo22sc con respaldo previo.
Este estado reemplaza la nota histórica de fallo DNS de arriba. Los pagos
históricos conservan importe y estado; su concepto requiere vinculación manual.
No se inició el receptor ni se realizaron pagos o envíos de prueba.
