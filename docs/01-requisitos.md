# Requisitos — inventario de ferretería (versión final)

**Estado:** versión final 1.0 (13-oct-2026), aprobada por el interesado («Requisitos - Requerimientos están completos»). Parte de la versión 3: los valores que allí eran **[ADOPTADO]** quedan **[ADOPTADO]** como línea base y se implementaron en el código de `inventario-ferreteria`. **Referencia:** ISO/IEC/IEEE 29148:2018 para identificar y documentar requisitos; ISO/IEC 25010:2023 para clasificar requisitos de calidad. **Plantilla:** cada requisito funcional sigue la plantilla de requerimientos (descripción, actor, prioridad, precondiciones, flujo principal, reglas de negocio, datos de entrada, criterios de aceptación).

**Convenciones:**

* **[DECIDIDO]**: confirmado expresamente por el interesado.
* **[ADOPTADO]**: valor propuesto por el analista en la versión 3 y aceptado con la aprobación de la versión final. Cambiarlo es una solicitud de cambio (nuevo requisito o ADR), no una corrección.
* **Pendiente de producción**: dato de la empresa que no bloquea el desarrollo (sección 10); el sistema ya lo deja configurable.

Documentos relacionados: diseño en [02-analisis-diseno.md](02-analisis-diseno.md), arquitectura en [03-arquitectura.md](03-arquitectura.md), base de datos en [04-base-de-datos.md](04-base-de-datos.md) y trazabilidad requisito → prueba en [05-pruebas.md](05-pruebas.md).

---

## 1. Decisiones incorporadas en esta versión

| # | Decisión del interesado [DECIDIDO] | Consecuencia en los requisitos |
|---|---|---|
| D-01 | El inventario solamente incluye compras y ventas. | Los únicos movimientos de negocio son entrada por compra y salida por venta. Ajustes y conteos físicos salen del alcance (sección 6). |
| D-02 | Una sola bodega. | No hay ubicaciones ni traslados. |
| D-03 | Diferentes marcas y presentaciones. | Producto genérico + referencia por marca y presentación (RF-PRO-001). |
| D-04 | Un solo código de barras. | Cada referencia tiene exactamente un código de barras, único en el catálogo. |
| D-05 | Fracciones y conversiones. | Unidad base por referencia y unidades alternas con factor (RF-PRO-003). |
| D-06 | Estados disponible, reservada, dañada; no se permiten negativos. | RF-EST-001 y RN-03. |
| D-07 | Productos con vencimiento o garantía. | Lotes con fecha de vencimiento; garantía en meses por referencia (RF-PRO-001, RF-VEN-001). |
| D-08 | Entrada por compra genera documento de ingreso; salida por venta genera factura. | RF-COM-001 y RF-VEN-001. |
| D-09 | Los cierres se programan mensualmente. | RF-CIE-001. |
| D-10 | Terceros: cédula o NIT, nombre, apellido, razón social, dirección, teléfono, correo. | RF-TER-001. |
| D-11 | Gestionar costos, precios e historial; incrementar un porcentaje al precio de compra por producto. | RF-PRE-001. |
| D-12 | Existen usuarios y permisos. | RF-SEG-001. |
| D-13 | Se migrarán datos desde un Excel. | RF-MIG-001. |
| D-14 | Se requiere conexión con sistemas externos, lectores de códigos y operación sin internet. | RF-INT-001, RF-LEC-001 y RF-OFF-001 pasan de candidatos a confirmados. |
| D-15 | El volumen esperado es alto. | RNF-REN-001. |
| D-16 | Se requiere auditoría de todos los movimientos. | RNF-SEG-002 y RF-AUD-001. |

### Cambios de la versión 3 respecto a la versión 2

* RF-MOV-001 (entrada) se integra en **RF-COM-001** y RF-MOV-002 (salida) en **RF-VEN-001**: la entrada solo existe como compra y la salida solo como venta (D-01).
* RF-AJU-001 (ajustes) y RF-CON-001 (conteo físico) pasan a **fuera del alcance** por D-01.
* Nuevos: **RF-PRO-003** (unidades y conversiones) y **RF-AUD-001** (consultar auditoría).
* Prioridades asignadas a todos los requisitos **[ADOPTADO]**.

---

## 2. Alcance

El sistema administra el inventario de una ferretería con **una sola bodega**. Las existencias solo cambian por **compras** (con documento de ingreso) y **ventas** (con factura). Dentro de la bodega, las existencias se clasifican como disponibles, reservadas o dañadas, sin saldos negativos. Incluye catálogo con marcas, presentaciones, fracciones y conversiones; terceros; costos y precios con historial; cierres mensuales; usuarios y permisos; migración desde Excel; integración con sistemas externos; lector de códigos de barras; operación sin internet; y auditoría de todos los movimientos.

## 3. Actores

| Actor | Participación [ADOPTADO] |
|---|---|
| Responsable de inventario | Catálogo, conversiones, estados de existencias, reservas, consulta de kárdex, cierre mensual. |
| Personal de compras | Proveedores y compras con documento de ingreso. |
| Personal de ventas | Clientes, ventas con factura y reservas. |
| Gerencia | Precios, costos, reportes, anulaciones, reapertura de cierres y auditoría. |
| Administrador | Usuarios, roles, permisos, dispositivos, migración y configuración de integraciones. |
| Sistema externo | Recibe o entrega información (por ejemplo, facturación electrónica o contabilidad). |
| Proceso programado | Ejecuta el cierre mensual y los reintentos de integración. |

---

## 4. Requisitos funcionales

## RF-PRO-001 — Registrar producto y referencia

### Descripción
El sistema deberá permitir registrar productos genéricos y, para cada uno, sus referencias por marca y presentación, cada referencia con un único código de barras, su unidad base, su control de vencimiento o garantía y su porcentaje de incremento.

### Actor
Responsable de inventario.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Usuario con permiso `PRODUCTO_EDITAR`; categorías, marcas, unidades y tarifas de impuesto registradas.

### Flujo principal
1. El actor solicita registrar un producto o agregar una referencia a un producto existente.
2. El sistema presenta los campos del producto y de la referencia.
3. El actor introduce los datos (o escanea el código de barras).
4. El sistema valida obligatoriedad, unicidad del código de barras y unicidad de la combinación producto + marca + presentación.
5. El sistema guarda el producto o la referencia con su unidad base como primera unidad de conversión.
6. El sistema confirma el resultado.

### Reglas de negocio
* RN-01: cada referencia (producto + marca + presentación) tiene un solo código de barras, único en todo el catálogo [DECIDIDO]. Dos presentaciones distintas no pueden compartir código; si el fabricante repite un código, se asigna un código interno **[ADOPTADO]**.
* RN-10: si el producto controla vencimiento, toda compra y venta de sus referencias exige lote con fecha de vencimiento [DECIDIDO el control; nivel por lote **ADOPTADO**].
* RN-11: la garantía se define en meses por referencia; la factura registra la fecha de fin de garantía de cada línea **[ADOPTADO]**.
* RN-05: unidad base obligatoria; si no admite fracción, la fracción mínima es 1.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| código interno | string | Sí | Código del producto genérico, único. |
| nombre | string | Sí | Denominación del producto. |
| categoría | referencia | Sí | Clasificación. |
| controla vencimiento | booleano | Sí | Obliga a manejar lotes con fecha de vencimiento. |
| marca | referencia | Sí | Marca de la referencia. |
| presentación | string | Sí | Por ejemplo «Galón 3,785 L», «Rollo 100 m». |
| código de barras | string (hasta 14) | Sí | Único en el catálogo. |
| unidad base | referencia | Sí | Unidad en que se controlan existencias (UND, M, KG…). |
| permite fracción / fracción mínima | booleano / decimal | Sí | Por ejemplo 0,5 m o 0,25 kg. |
| meses de garantía | entero | No | Vacío si no aplica. |
| tarifa de IVA | referencia | Sí | 19 %, 5 % o excluido **[ADOPTADO]**. |
| porcentaje de incremento | decimal | Sí | Margen sobre el costo de compra (RF-PRE-001). |
| stock mínimo | decimal | No | Alerta en la pantalla de inicio (RF-ALR-001). |

### Criterios de aceptación
* Un actor autorizado registra un producto con dos marcas y dos presentaciones, cada una con su código de barras.
* El sistema rechaza un código de barras ya existente e indica la referencia que lo tiene.
* El sistema rechaza una referencia duplicada (mismo producto, marca y presentación).
* La referencia registrada puede recuperarse por código de barras, nombre o marca.

## RF-PRO-002 — Buscar y consultar productos y existencias

### Descripción
El sistema deberá consultar referencias por código de barras, nombre, marca o categoría y mostrar sus existencias separadas por estado, y por lote cuando aplique.

### Actor
Todos los usuarios autenticados; costos solo con permiso `COSTO_VER`.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Existe al menos una referencia registrada.

### Flujo principal
1. El actor abre la consulta e introduce un criterio o escanea un código.
2. El sistema busca coincidencias.
3. El sistema presenta referencias con existencias disponible, reservada, dañada y total en unidad base; precio vigente; y costo si el actor tiene permiso.
4. El actor consulta el detalle por lote y fecha de vencimiento.

### Reglas de negocio
* RN-08: una sola bodega; el desglose es por estado y lote.
* RN-12: costos y márgenes visibles solo con permiso `COSTO_VER` **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| criterio | string | Sí | Código de barras, código interno, nombre o marca. |
| categoría | referencia | No | Filtro. |
| estado | enumeración | No | Disponible, reservada o dañada. |
| próximos a vencer (días) | entero | No | Filtra lotes que vencen dentro del plazo. |

### Criterios de aceptación
* La búsqueda por código de barras devuelve exactamente una referencia.
* Las existencias mostradas coinciden con la suma de movimientos de la referencia.
* Un usuario sin `COSTO_VER` no ve costos ni márgenes.

## RF-PRO-003 — Gestionar unidades y conversiones

### Descripción
El sistema deberá permitir definir, para cada referencia, las unidades en que se compra o vende y su factor de conversión a la unidad base, para operar con fracciones.

### Actor
Responsable de inventario.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Referencia registrada; unidades de medida configuradas.

### Flujo principal
1. El actor selecciona la referencia.
2. El sistema muestra la unidad base y las conversiones existentes.
3. El actor agrega una unidad, su factor a la unidad base y si se usa en compras, ventas o ambas.
4. El sistema valida el factor y guarda la conversión.

### Reglas de negocio
* RN-05: las existencias, el kárdex y los costos se guardan en unidad base [DECIDIDO fracciones y conversiones; mecanismo **ADOPTADO**].
* El factor es mayor que cero. Cada línea de documento conserva el factor usado, de modo que un cambio posterior no altera documentos anteriores.
* Las cantidades se guardan con 4 decimales; la cantidad vendida debe ser múltiplo de la fracción mínima **[ADOPTADO]**.
* La libra equivale a 500 g por convención local **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| referencia | referencia | Sí | Referencia a la que aplica. |
| unidad | referencia | Sí | ROLLO, CAJA, BULTO, LB, etc. |
| factor a base | decimal | Sí | Ej.: ROLLO = 100 M; BULTO = 50 KG; LB = 0,5 KG. |
| uso en compra / venta | booleano | Sí | Dónde se permite la unidad. |

### Criterios de aceptación
* Comprar 2 rollos de cable (factor 100) aumenta el disponible en 200 m.
* Vender 6 libras de puntilla (factor 0,5) disminuye el disponible en 3 kg.
* Vender 12,5 m de un cable con fracción mínima 0,5 m es aceptado; 12,3 m es rechazado.

## RF-LEC-001 — Leer código de barras

### Descripción
El sistema deberá usar el lector de códigos para localizar la referencia en compras, ventas, reservas y consultas.

### Actor
Personal de compras, ventas e inventario.

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Lector conectado al equipo; referencia con código registrado.

### Flujo principal
1. El actor escanea el código.
2. El sistema recibe el código como entrada de teclado.
3. El sistema busca la referencia y la agrega a la operación en curso o informa que no existe.

### Reglas de negocio
* Un solo código por referencia [DECIDIDO].
* Lector tipo teclado (USB o Bluetooth), sin controlador especial **[ADOPTADO]**.
* Funciona igual sin internet, con el catálogo almacenado en el equipo.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| código leído | string | Sí | EAN-13, EAN-8, UPC o código interno. |

### Criterios de aceptación
* Un código registrado agrega la referencia correcta a la operación.
* Un código desconocido se informa y no modifica la operación ni las existencias.

## RF-LEC-002 — Generar e imprimir códigos de barras

### Descripción
El sistema deberá proponer un código de barras interno para las referencias que no traen código del fabricante (o lo traen repetido, RN-01) e imprimir etiquetas con el código para pegarlas en el producto o en la estantería.

### Actor
Personal de inventario con permiso `PRODUCTO_EDITAR` (generar); cualquier usuario con sesión (imprimir).

### Prioridad
Media **[ADOPTADO]**.

### Precondiciones
Para imprimir, la referencia tiene un código registrado; para generar, el usuario está creando una referencia.

### Flujo principal
1. Al registrar una referencia, el actor pide «Generar» y el sistema propone el siguiente código interno libre.
2. El actor guarda la referencia; la unicidad del código la sigue garantizando la base de datos.
3. Desde el detalle de la referencia o desde la lista de productos, el actor elige copias, tamaño de etiqueta y si muestra el precio con IVA.
4. El sistema muestra una vista previa y abre la impresión.

### Reglas de negocio
* Código interno = EAN-13 con prefijo GS1 de circulación restringida 299 + secuencia de 9 dígitos + dígito de control (los datos demo ya usan esta serie) **[ADOPTADO]**.
* El código propuesto no se reserva: si dos personas generan a la vez, la segunda recibe el aviso de código repetido al guardar y vuelve a generar.
* Se dibuja como EAN-13 si el código cumple el estándar; cualquier otro código registrado se imprime como Code 128.
* Tamaños: hoja carta con 3 etiquetas por fila (63 × 30 mm) o rollo térmico de 50 × 25 mm; hasta 500 copias por referencia.

### Criterios de aceptación
* Dado un catálogo cuyo mayor código interno es 2990000000101, cuando el actor pide generar, entonces recibe 2990000000118 y ninguna referencia lo usa.
* Dado un usuario sin `PRODUCTO_EDITAR`, cuando pide generar, entonces recibe 403.
* Dada una referencia con código EAN-13 válido, cuando se imprimen 3 copias, entonces salen 3 etiquetas con nombre, presentación, código legible por el lector y, si se pidió, precio con IVA.

## RF-TER-001 — Registrar clientes y proveedores

### Descripción
El sistema deberá registrar terceros (clientes, proveedores o ambos) con su identificación y datos de contacto según el tipo de persona.

### Actor
Personal de compras o ventas con permiso `TERCERO_EDITAR`.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Usuario autorizado.

### Flujo principal
1. El actor selecciona el tipo de persona y el tipo de identificación.
2. El sistema muestra los campos obligatorios para ese tipo.
3. El actor introduce los datos.
4. El sistema valida formato, dígito de verificación del NIT y duplicados.
5. El sistema guarda el tercero y confirma.

### Reglas de negocio
* RN-13: persona natural: cédula (o CE/pasaporte), nombre y apellido obligatorios [DECIDIDO campos; obligatoriedad **ADOPTADO**].
* RN-14: persona jurídica: NIT con dígito de verificación y razón social obligatorios **[ADOPTADO]**.
* Dirección, teléfono y correo: obligatorios para proveedores y para clientes que requieran factura a su nombre **[ADOPTADO]**; el correo es necesario si se emite factura electrónica.
* Un mismo tercero puede ser cliente y proveedor; tipo + número de identificación es único **[ADOPTADO]**.
* Se mantiene un tercero «Consumidor final» para ventas de mostrador sin identificación **[ADOPTADO]** (identificación 222222222222).

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| tipo de persona | enumeración | Sí | Natural o jurídica. |
| tipo de identificación | enumeración | Sí | CC, CE, NIT, pasaporte. |
| número (cédula o NIT) | string | Sí | Único por tipo. |
| dígito de verificación | entero | Condicional | Obligatorio con NIT. |
| nombre | string | Condicional | Obligatorio para persona natural. |
| apellido | string | Condicional | Obligatorio para persona natural. |
| razón social | string | Condicional | Obligatoria para persona jurídica. |
| dirección | string | Condicional | Ver reglas. |
| teléfono | string | Condicional | Ver reglas. |
| correo | string | Condicional | Formato válido; ver reglas. |
| es cliente / es proveedor | booleano | Sí | Al menos uno. |

### Criterios de aceptación
* Se registra una persona natural con cédula, nombre y apellido.
* Se rechaza una persona jurídica sin razón social o sin dígito de verificación.
* Se rechaza una identificación ya registrada con el mismo tipo.
* Un tercero marcado como cliente y proveedor aparece en compras y en ventas.

## RF-COM-001 — Registrar compra y generar documento de ingreso

### Descripción
El sistema deberá registrar las compras a proveedores y, al confirmarlas, generar un documento de ingreso numerado y aumentar las existencias disponibles de cada referencia recibida.

### Actor
Personal de compras (registra y confirma).

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Proveedor y referencias registrados; usuario con `COMPRA_REGISTRAR`; mes de la fecha efectiva abierto.

### Flujo principal
1. El actor selecciona el proveedor e introduce número y fecha de la factura del proveedor.
2. El actor escanea o busca cada referencia e indica unidad, cantidad, costo unitario sin IVA y, si el producto vence, lote y fecha de vencimiento.
3. El sistema convierte cada cantidad a unidad base, calcula subtotal, IVA y total.
4. El actor confirma.
5. El sistema asigna el consecutivo de documento de ingreso, registra un movimiento de entrada por línea en estado disponible, actualiza costo promedio, último costo y precio de venta (RF-PRE-001).
6. El sistema presenta el documento de ingreso para consulta o impresión.

### Reglas de negocio
* RN-02: cada línea confirmada genera un único movimiento `ENTRADA_COMPRA` [DECIDIDO].
* El documento de ingreso tiene prefijo y consecutivo propios (ej. ING-1) **[ADOPTADO]**.
* La factura del proveedor no puede registrarse dos veces para el mismo proveedor **[ADOPTADO]**.
* Una compra confirmada no se edita; solo se anula con un movimiento de reverso, si hay saldo para revertir y con permiso `COMPRA_ANULAR` **[ADOPTADO]**.
* Recepción parcial, pedidos y órdenes de compra: fuera del alcance de esta versión **[ADOPTADO]**.
* La operación es atómica: si una línea falla, no se guarda nada.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| proveedor | referencia | Sí | Tercero marcado como proveedor. |
| número factura proveedor | string | Sí | Soporte de la compra. |
| fecha factura proveedor | fecha | Sí | |
| fecha efectiva | fecha/hora | Sí | Por defecto, ahora; debe caer en mes abierto. |
| referencia | referencia | Sí | Por línea. |
| unidad | referencia | Sí | Unidad con uso de compra. |
| cantidad | decimal | Sí | Mayor que cero. |
| costo unitario | monetario | Sí | Sin IVA, por unidad comprada. |
| lote / fecha de vencimiento | string / fecha | Condicional | Obligatorio si el producto controla vencimiento. |

### Criterios de aceptación
* Una compra confirmada produce un documento de ingreso consultable y un movimiento por línea.
* Comprar 10 bultos de cemento (50 kg) aumenta el disponible en 500 kg una sola vez.
* Una compra de producto con vencimiento sin lote es rechazada.
* Un fallo durante la confirmación no deja documento ni existencias modificadas.

## RF-VEN-001 — Registrar venta y generar factura

### Descripción
El sistema deberá registrar ventas a clientes, verificar existencias suficientes, generar la factura numerada y disminuir las existencias de cada referencia vendida.

### Actor
Personal de ventas.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Cliente (o consumidor final) y referencias registrados; usuario con `VENTA_EMITIR`; rango de facturación vigente; mes abierto.

### Flujo principal
1. El actor selecciona el cliente.
2. El actor escanea cada referencia e indica unidad y cantidad (incluidas fracciones).
3. El sistema propone el lote que vence primero cuando aplica, calcula precio, IVA y total, y verifica existencias disponibles (o reservadas para ese cliente).
4. El actor confirma.
5. El sistema asigna el consecutivo de factura, registra un movimiento de salida por línea, guarda el costo de venta y la fecha de fin de garantía cuando aplica.
6. El sistema emite la factura (y la envía al sistema de facturación electrónica si se define así) y la presenta.

### Reglas de negocio
* RN-03: no se permiten saldos negativos [DECIDIDO]; la venta sale de disponible o de una reserva del mismo cliente; las existencias dañadas no se venden **[ADOPTADO]**.
* RN-10: no se vende un lote vencido **[ADOPTADO]**; el lote sugerido es el de vencimiento más próximo **[ADOPTADO]**.
* RN-11: la línea registra la fecha de fin de garantía = fecha de factura + meses de garantía.
* El precio sugerido es el vigente (RF-PRE-001); descuentos por línea solo con permiso `PRECIO_CAMBIAR` **[ADOPTADO]**.
* Una factura emitida no se edita; se anula con reverso y permiso `VENTA_ANULAR`; notas crédito y devoluciones quedan fuera del alcance (RF-DEV-001).
* Forma de pago: contado o crédito; el detalle de medios de pago queda fuera del alcance.
* Factura electrónica: el sistema deja el evento `VENTA_EMITIDA` para el proveedor tecnológico; resolución DIAN configurable (pendiente de producción).

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| cliente | referencia | Sí | Tercero cliente o consumidor final. |
| referencia | referencia | Sí | Por línea. |
| unidad | referencia | Sí | Unidad con uso de venta. |
| cantidad | decimal | Sí | Mayor que cero, múltiplo de la fracción mínima. |
| lote | referencia | Condicional | Si el producto controla vencimiento. |
| precio unitario | monetario | Sí | Propuesto por el sistema, sin IVA. |
| descuento | monetario | No | Sujeto a permiso. |
| forma de pago | enumeración | Sí | Contado o crédito. |

### Criterios de aceptación
* Una venta confirmada produce una factura consultable y un movimiento de salida por línea.
* Vender 12,5 m de cable con 200 m disponibles deja 187,5 m.
* No se confirma una venta que deje cualquier saldo negativo; se informa la referencia y el saldo.
* No se vende un lote vencido ni existencias dañadas.
* Una venta de un taladro con 12 meses de garantía registra la fecha de fin de garantía.

## RF-EST-001 — Gestionar estados de existencias (reservas y dañados)

### Descripción
El sistema deberá reservar y liberar existencias para un cliente y clasificar existencias como dañadas, cambiando su estado sin alterar el total ni generar negativos.

### Actor
Ventas (reservas) y responsable de inventario (dañados).

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Referencia con saldo en el estado origen; permiso `RESERVA_GESTIONAR` o `ESTADO_DANADO`.

### Flujo principal
1. El actor selecciona referencia, lote si aplica, estado origen, estado destino, cantidad y motivo (o cliente, para reservas).
2. El sistema valida saldo y permisos.
3. El sistema registra el cambio de estado auditado y presenta los nuevos saldos.

### Reglas de negocio
* RN-03: ningún cambio de estado deja saldo negativo [DECIDIDO].
* El cambio de estado es un registro de soporte, no un tercer tipo de movimiento de negocio: no cambia el total **[ADOPTADO, coherente con D-01]**.
* Una reserva vence a las 72 horas y se libera automáticamente; al facturar se consume **[ADOPTADO]**.
* Lo dañado no se vende; su salida definitiva (baja o devolución al proveedor) queda fuera del alcance (RF-BAJ-001), porque D-01 no contempla otros movimientos; puede recuperarse a disponible.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| referencia / lote | referencia | Sí / condicional | |
| estado origen y destino | enumeración | Sí | Disponible, reservada, dañada; distintos. |
| cantidad | decimal | Sí | En unidad base. |
| cliente | referencia | Condicional | Obligatorio para reservas. |
| motivo | texto | Sí | Para dañados. |

### Criterios de aceptación
* Reservar 2 unidades pasa 2 de disponible a reservada; el total no cambia.
* No es posible reservar o dañar más de lo disponible.
* Cada cambio de estado figura en la auditoría con usuario y fecha.

## RF-MOV-003 — Consultar historial de movimientos (kárdex)

### Descripción
El sistema deberá mostrar el kárdex de cada referencia: entradas, salidas y cambios de estado, con saldo y costo promedio después de cada movimiento.

### Actor
Responsable de inventario y gerencia.

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Existen movimientos registrados.

### Flujo principal
1. El actor selecciona referencia, intervalo de fechas y, opcionalmente, tipo de movimiento o documento.
2. El sistema presenta los movimientos con documento, usuario, cantidad, saldo y costo.
3. El actor abre el documento origen (ingreso o factura).

### Reglas de negocio
* RN-02: cada movimiento conserva tipo, documento, usuario, dispositivo y origen.
* RN-06: los movimientos son inmutables; una anulación aparece como movimiento de reverso enlazado al original **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| referencia | referencia | No | Filtro. |
| intervalo | fechas | Sí | Filtro temporal. |
| tipo / documento | enumeración / string | No | Filtros adicionales. |

### Criterios de aceptación
* El saldo final del kárdex coincide con la existencia actual.
* Desde cada movimiento se abre su documento origen.

## RF-PRE-001 — Calcular precio y conservar historial de costos y precios

### Descripción
El sistema deberá calcular el precio de venta de cada referencia aplicando su porcentaje de incremento al costo de compra y conservar el historial de costos y precios.

### Actor
Gerencia (porcentajes y precios manuales); el sistema (recálculo tras cada compra).

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Referencia con porcentaje de incremento; usuario con `PRECIO_CAMBIAR` para cambios manuales.

### Flujo principal
1. Al confirmarse una compra, el sistema toma el último costo de compra en unidad base.
2. El sistema calcula precio = costo × (1 + porcentaje / 100) y lo redondea.
3. Si el precio cambia, el sistema cierra la versión vigente y guarda una nueva con costo, porcentaje, precio, motivo, fecha y usuario.
4. Gerencia puede cambiar el porcentaje o fijar un precio manual; el sistema versiona el cambio del mismo modo.

### Reglas de negocio
* El porcentaje de incremento se define por referencia [DECIDIDO].
* Base del cálculo: último costo de compra sin IVA **[ADOPTADO]**; alternativa: costo promedio.
* Redondeo hacia arriba al múltiplo de 50 pesos **[ADOPTADO]**.
* Precios y costos se guardan sin IVA; el IVA se agrega en la factura **[ADOPTADO]**.
* Valoración del inventario por costo promedio ponderado **[ADOPTADO]**.
* Solo un precio vigente por referencia.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| referencia | referencia | Sí | |
| porcentaje de incremento | decimal | Sí | Mayor o igual a cero. |
| precio manual | monetario | No | Requiere permiso y motivo. |

### Criterios de aceptación
* Con costo 5.200 y 45 %, el precio es 7.550 (7.540 redondeado al múltiplo de 50).
* El historial conserva valores anteriores con fecha, usuario y motivo.
* Un usuario sin `PRECIO_CAMBIAR` no puede cambiar porcentajes ni precios.

## RF-CIE-001 — Ejecutar cierre mensual programado

### Descripción
El sistema deberá ejecutar automáticamente el cierre de cada mes, guardar la fotografía de existencias y costos, e impedir movimientos con fecha en meses cerrados.

### Actor
Proceso programado; responsable de inventario (ejecución manual); gerencia (reapertura).

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Mes abierto; sin operaciones fuera de línea pendientes de ese mes.

### Flujo principal
1. En la fecha programada, el sistema verifica operaciones sin internet pendientes o en conflicto.
2. Si hay pendientes, el sistema aplaza el cierre y avisa al responsable.
3. Si no hay, el sistema calcula y guarda saldos por referencia, lote y estado con su costo promedio y valor.
4. El sistema marca el mes como cerrado y genera la constancia (quién, cuándo, cantidad de saldos).

### Reglas de negocio
* RN-07: cierre programado el día 1 de cada mes a las 02:00 (hora de Colombia) para el mes anterior **[ADOPTADO; mensual DECIDIDO]**.
* Un mes cerrado no admite movimientos con fecha efectiva en él.
* Solo gerencia (`CIERRE_REABRIR`) reabre un mes, con motivo obligatorio; al volver a cerrarlo se recalcula la fotografía **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| mes | período AAAAMM | Sí | Mes a cerrar. |
| responsable | usuario | Sí | Proceso o usuario que ejecuta. |
| motivo de reapertura | texto | Condicional | Obligatorio al reabrir. |

### Criterios de aceptación
* Tras cerrar septiembre, un movimiento con fecha de septiembre es rechazado.
* La fotografía del cierre coincide con el kárdex a la fecha de corte.
* Se conserva quién cerró y reabrió, cuándo y por qué.

## RF-SEG-001 — Gestionar usuarios, roles y permisos

### Descripción
El sistema deberá administrar usuarios, roles y permisos y aplicar los permisos en cada operación.

### Actor
Administrador.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Administrador autenticado con `USUARIO_ADMINISTRAR`.

### Flujo principal
1. El administrador crea o selecciona un usuario.
2. Le asigna uno o varios roles.
3. El sistema valida y guarda los cambios.
4. Los permisos se aplican desde el siguiente acceso del usuario.

### Reglas de negocio
* Existen usuarios y permisos [DECIDIDO].
* Matriz de roles propuesta en la sección 5 **[ADOPTADO]**.
* Los usuarios no se borran; se desactivan, para conservar la trazabilidad.
* Contraseñas almacenadas con hash robusto (Argon2id o bcrypt) **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| usuario (login) | string | Sí | Único. |
| nombre completo | string | Sí | |
| correo | string | No | |
| roles | lista | Sí | Al menos uno. |
| activo | booleano | Sí | |

### Criterios de aceptación
* Un vendedor no puede confirmar compras, cambiar precios ni ver costos.
* Un usuario desactivado no puede iniciar sesión, y sus movimientos anteriores siguen consultables.

## RF-AUD-001 — Consultar auditoría

### Descripción
El sistema deberá registrar automáticamente toda creación, modificación o anulación de movimientos, documentos, precios, terceros, catálogo, permisos y cierres, y permitir su consulta.

### Actor
Gerencia con `AUDITORIA_CONSULTAR`.

### Prioridad
Crítica **[ADOPTADO]**.

### Precondiciones
Usuario autorizado.

### Flujo principal
1. El actor filtra por fecha, usuario, tabla, registro o acción.
2. El sistema presenta los eventos con valores antes y después.

### Reglas de negocio
* Auditoría de todos los movimientos [DECIDIDO]; se extiende a documentos, precios, terceros, catálogo, permisos y cierres **[ADOPTADO]**.
* Cada evento guarda usuario, fecha y hora, dispositivo, origen (en línea, sin internet, migración, integración), tabla, registro, acción y valores antes/después.
* Nadie puede modificar ni borrar la auditoría.
* Conservación: mínimo 5 años **[ADOPTADO]**.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| intervalo | fechas | Sí | |
| usuario / tabla / acción | filtros | No | |

### Criterios de aceptación
* Cada movimiento registrado tiene su evento de auditoría.
* Un intento de modificar o borrar la auditoría es rechazado.

## RF-MIG-001 — Migrar datos desde Excel

### Descripción
El sistema deberá cargar catálogo, terceros y saldos iniciales desde un libro de Excel, validando cada fila y reportando errores, sin cargar dos veces el mismo archivo.

### Actor
Administrador con `MIGRACION_EJECUTAR`.

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Archivo en la plantilla que descarga el propio sistema; fecha de corte definida en un período abierto.

### Flujo principal
1. El actor carga el archivo e indica la fecha de corte.
2. El sistema guarda cada fila original y la valida.
3. El sistema presenta filas válidas y filas con error y su motivo.
4. El actor confirma la carga de las filas válidas.
5. El sistema crea catálogo y terceros y registra los saldos iniciales como movimientos `SALDO_INICIAL` en la fecha de corte; el mes de corte se cierra luego con RF-CIE-001.
6. El sistema entrega el resumen de conciliación (filas, cantidades y valores contra el archivo).

### Reglas de negocio
* Se migra desde Excel [DECIDIDO]; plantilla de tres hojas: TERCEROS, PRODUCTOS (producto, referencia y una unidad alterna por fila) y SALDOS (con lote y vencimiento) **[ADOPTADO]**.
* El mismo archivo (mismo hash) no se carga dos veces.
* Los saldos iniciales entran como disponibles con su costo; no se admiten cantidades negativas.
* Duplicados de código de barras o identificación se rechazan y se reportan.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| archivo | .xlsx | Sí | Según plantilla. |
| fecha de corte | fecha | Sí | Fecha de los saldos iniciales. |

### Criterios de aceptación
* Las filas válidas se cargan y las inválidas quedan listadas con su motivo.
* La suma de cantidades y valores cargados coincide con el archivo, excluidas las filas con error.
* Volver a cargar el mismo archivo es rechazado.

## RF-INT-001 — Integrar sistemas externos

### Descripción
El sistema deberá intercambiar información de ventas, compras, terceros o catálogo con los sistemas externos que defina la empresa, sin duplicar operaciones.

### Actor
Sistema externo; administrador (configuración).

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Sistema externo configurado; formato de intercambio acordado.

### Flujo principal
1. Al confirmarse una operación, el sistema registra el evento en la bandeja de salida dentro de la misma transacción.
2. Un proceso envía el evento con su clave de idempotencia.
3. El sistema registra el resultado; si falla, reintenta con espera creciente y, tras varios fallos, avisa al administrador.
4. Para datos recibidos, el sistema valida el identificador externo y aplica la operación una sola vez.

### Reglas de negocio
* Integración requerida [DECIDIDO]; sistemas concretos configurables (pendiente de producción) (candidatos: facturación electrónica, contabilidad, tienda en línea).
* Cada evento tiene clave única; reenviarlo no lo duplica.
* `mapeo_externo` relaciona identificadores internos y externos.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| sistema y tipo de evento | catálogo | Sí | |
| identificador externo | string | Sí | |
| datos del evento | JSON | Sí | JSON del documento con clave de idempotencia; el contrato fino se acuerda con cada sistema. |

### Criterios de aceptación
* Un evento enviado dos veces se aplica una sola vez.
* Una caída del sistema externo no impide vender; los eventos se envían al recuperarse.

## RF-OFF-001 — Operar sin internet y sincronizar

### Descripción
El sistema deberá permitir vender, comprar, reservar y consultar en equipos autorizados sin conexión, y sincronizar las operaciones al recuperarla.

### Actor
Personal autorizado; servicio de sincronización.

### Prioridad
Alta **[ADOPTADO]**.

### Precondiciones
Equipo registrado y autorizado para operar sin conexión; catálogo, precios y existencias sincronizados previamente; rango de numeración asignado al equipo.

### Flujo principal
1. Sin conexión, el actor realiza la operación; el equipo valida con su copia local de existencias, numera con su rango propio y guarda la operación con un identificador único.
2. Al volver la conexión, el equipo envía las operaciones pendientes.
3. El servidor aplica cada operación una sola vez y vuelve a validar existencias.
4. Las operaciones que violen una regla (por ejemplo, negativo por ventas simultáneas) quedan en conflicto para resolución por el responsable.

### Reglas de negocio
* Operación sin internet requerida [DECIDIDO]; operaciones permitidas: venta, compra, reserva, consulta, registro de cliente **[ADOPTADO]**.
* Identificador único generado en el equipo (UUID v7) **[ADOPTADO]**.
* Rango de numeración de facturas por equipo (ej. FC1 del 1 al 5.000) **[ADOPTADO]**; la resolución DIAN se configura por equipo.
* La regla de no negativos se aplica de nuevo en el servidor; los conflictos no se descartan, se resuelven y quedan auditados.
* No se cierra un mes con operaciones pendientes o en conflicto de ese mes.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| operación | enumeración | Sí | Venta, compra, reserva, tercero. |
| identificador de sincronización | UUID | Sí | Generado en el equipo. |
| equipo y usuario | referencia | Sí | |
| fecha local | fecha/hora | Sí | Momento real de la operación. |

### Criterios de aceptación
* Reenviar la misma operación no la duplica.
* Dos cajas sin conexión que venden la última unidad generan un conflicto y ningún saldo negativo.
* Las operaciones sincronizadas aparecen en la auditoría con origen «sin internet».

## RF-REP-001 — Consultar reportes de inventario

### Descripción
El sistema deberá generar reportes de existencias, valoración, ventas, compras, márgenes y vencimientos.

### Actor
Gerencia y responsable de inventario.

### Prioridad
Media **[ADOPTADO]**.

### Precondiciones
Datos registrados; permiso `REPORTE_CONSULTAR`.

### Flujo principal
1. El actor selecciona reporte y parámetros.
2. El sistema calcula y presenta los resultados.
3. El actor exporta a Excel o PDF **[ADOPTADO]**.

### Reglas de negocio
* Reportes propuestos: existencias por estado, valoración al cierre, kárdex, ventas y compras por período, margen por referencia, lotes próximos a vencer, garantías vigentes **[ADOPTADO]**.
* Costos y márgenes solo con `COSTO_VER`.

### Datos de entrada
| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| tipo de reporte | catálogo | Sí | |
| período o fecha de corte | fecha | Sí | |
| filtros | varios | No | Categoría, marca, referencia. |

### Criterios de aceptación
* La valoración de un mes cerrado coincide con la fotografía del cierre.
* Los filtros y permisos se aplican correctamente.

---

## 5. Matriz de permisos [ADOPTADO]

| Permiso | Admin | Inventario | Compras | Ventas | Gerencia |
|---|---|---|---|---|---|
| Usuarios y roles | ✔ | | | | |
| Productos, referencias, conversiones | ✔ | ✔ | | | |
| Terceros | ✔ | | ✔ | ✔ | |
| Registrar compras | ✔ | | ✔ | | |
| Anular compras | ✔ | | | | ✔ |
| Emitir facturas | ✔ | | | ✔ | |
| Anular facturas | ✔ | | | | ✔ |
| Reservas | ✔ | ✔ | | ✔ | |
| Marcar dañados | ✔ | ✔ | | | |
| Ver costos | ✔ | ✔ | ✔ | | ✔ |
| Cambiar precios y porcentajes | ✔ | | | | ✔ |
| Ejecutar cierre | ✔ | ✔ | | | |
| Reabrir cierre | ✔ | | | | ✔ |
| Migración | ✔ | | | | |
| Auditoría | ✔ | | | | ✔ |
| Reportes | ✔ | ✔ | | | ✔ |

---

## 6. Fuera del alcance

| ID | Funcionalidad | Situación |
|---|---|---|
| RF-AJU-001 | Ajustes de inventario. | Fuera del alcance por D-01, aceptado con la versión final. **Riesgo conocido:** pérdidas, robos o errores de conteo no se corrigen en el sistema; si se necesita, entra como solicitud de cambio. |
| RF-CON-001 | Conteo físico. | Fuera del alcance por D-01 (depende de los ajustes). |
| RF-UBI-001 | Traslados entre bodegas. | Fuera del alcance: una sola bodega (D-02). |
| RF-DEV-001 | Devoluciones de clientes y a proveedores. | Fuera del alcance; hoy se resuelven con la anulación del documento completo. |
| RF-BAJ-001 | Baja de existencias dañadas. | Fuera del alcance: lo dañado no sale del inventario; puede recuperarse a disponible. |
| RF-ALR-001 | Alertas de stock mínimo y de vencimiento. | Incluido como consulta: la pantalla de inicio muestra referencias bajo el mínimo y lotes que vencen en 90 días. Avisos por correo, fuera del alcance. |

---

## 7. Requisitos no funcionales — ISO/IEC 25010:2023

Los valores numéricos son **[ADOPTADO]** como meta de diseño; se recalibran cuando la empresa confirme el volumen real (sección 10).

| ID | Característica | Descripción | Prioridad | Criterio de aceptación |
|---|---|---|---|---|
| RNF-SEG-001 | Seguridad | Autenticación con usuario y contraseña; permisos por rol en cada operación. | Crítica | Pruebas de acceso de la matriz de permisos superadas al 100 %. |
| RNF-SEG-002 | Seguridad | Auditoría de todos los movimientos, documentos, precios, permisos y cierres, incluidas las operaciones sin internet sincronizadas. | Crítica | 100 % de movimientos con evento de auditoría; auditoría no modificable; conservación de 5 años. |
| RNF-REN-001 | Eficiencia de desempeño | Volumen alto [DECIDIDO]. Dimensionamiento propuesto: 50.000 referencias, 20.000 movimientos diarios, 30 usuarios simultáneos, 5 años de histórico en línea. | Alta | Búsqueda por código de barras < 300 ms (p95); confirmación de venta de 20 líneas < 2 s (p95); migración de 50.000 filas < 10 min. |
| RNF-FIA-001 | Fiabilidad | Sin cambios parciales de existencias ante fallos. | Crítica | Pruebas de interrupción en compra y venta: ningún documento sin movimientos ni movimientos sin documento. |
| RNF-REC-001 | Fiabilidad | Recuperación de servicio y datos. | Alta | Disponibilidad 99,5 % en horario comercial; RPO ≤ 15 min; RTO ≤ 4 h; prueba de restauración trimestral. |
| RNF-INT-001 | Capacidad de interacción | Venta de mostrador rápida con lector. | Alta | Un vendedor entrenado registra una venta de 5 líneas con lector en < 60 s. |
| RNF-COM-001 | Compatibilidad | Lectores tipo teclado; integración con sistemas externos; operación sin internet. | Alta | Lectores USB/Bluetooth probados; sincronización tras 8 h sin conexión sin duplicados. |
| RNF-MAN-001 | Mantenibilidad | Particiones mensuales del kárdex y la auditoría creadas automáticamente. | Media | Siempre existen al menos 3 particiones futuras. |

---

## 8. Reglas de negocio

| ID | Regla | Estado |
|---|---|---|
| RN-01 | Cada referencia (producto + marca + presentación) tiene un solo código de barras, único en el catálogo. | DECIDIDO (unicidad global: ADOPTADO) |
| RN-02 | Las existencias solo cambian por movimientos de compra o venta asociados a su documento; cada movimiento es inmutable y auditado. | DECIDIDO |
| RN-03 | No hay saldos negativos en ningún estado; se vende desde disponible o desde una reserva del cliente; lo dañado no se vende. | DECIDIDO (origen de la venta: ADOPTADO) |
| RN-04 | Ajustes fuera del alcance. | DECIDIDO por D-01 |
| RN-05 | Existencias en unidad base con 4 decimales; conversiones por referencia con factor; fracción mínima por referencia. | DECIDIDO (precisión: ADOPTADO) |
| RN-06 | Las correcciones se hacen por anulación con movimiento de reverso; nunca editando. | ADOPTADO |
| RN-07 | Cierre mensual programado; mes cerrado no admite movimientos; reapertura solo por gerencia con motivo. | DECIDIDO mensual (resto: ADOPTADO) |
| RN-08 | Una sola bodega; existencias por estado y lote. | DECIDIDO |
| RN-09 | Alertas por stock mínimo y vencimiento visibles en el inicio. | ADOPTADO |
| RN-10 | Producto con vencimiento exige lote; no se vende un lote vencido; se sugiere el que vence primero. | ADOPTADO |
| RN-11 | Garantía en meses por referencia; fecha de fin en cada línea de factura. | ADOPTADO |
| RN-12 | Costos visibles solo con permiso. | ADOPTADO |
| RN-13 | Persona natural: identificación, nombre y apellido obligatorios. | ADOPTADO |
| RN-14 | Persona jurídica: NIT con dígito de verificación y razón social obligatorios. | ADOPTADO |
| RN-15 | Precio = último costo × (1 + % de la referencia / 100), redondeado hacia arriba a múltiplos de 50; cada cambio queda en el historial. | DECIDIDO % por producto (base y redondeo: ADOPTADO) |
| RN-16 | Valoración por costo promedio ponderado. | ADOPTADO |

---

## 9. Supuestos adoptados en la versión final

Las preguntas de la versión 2 quedaron resueltas por decisión del interesado o por un valor adoptado:

| Tema | Línea base adoptada |
|---|---|
| Factura | El sistema numera, guarda e imprime la factura (prefijo FE en línea, prefijo por equipo sin internet) y deja un evento `VENTA_EMITIDA` en la bandeja de integración para un proveedor de factura electrónica. |
| Referencias y códigos | Una referencia por marca y presentación con un único código de barras global; si el fabricante repite código, se asigna uno interno. |
| Unidades | Unidad base por referencia, factores por referencia, 4 decimales, fracción mínima por referencia. |
| Reservas | Por cliente, vencen a las 72 h y se liberan solas; la venta puede consumir la reserva del mismo cliente; lo dañado no se vende. |
| Vencimiento y garantía | Vencimiento por lote con selección FEFO y bloqueo de lotes vencidos; garantía en meses por referencia con fecha de fin en cada línea de la factura. |
| Compra | Documento de ingreso con consecutivo ING + número de factura del proveedor; sin pedidos ni recepción parcial. |
| Factura: impuestos, descuentos y pagos | IVA por referencia (19 %, 5 %, excluido), descuento por línea con permiso `PRECIO_CAMBIAR`, forma de pago contado o crédito, anulación por reverso. |
| Terceros | RN-13 y RN-14; un tercero puede ser cliente y proveedor; el proveedor exige dirección, teléfono y correo. |
| Precio | RN-15 sobre el último costo, sin IVA, redondeo hacia arriba a múltiplos de 50; cambio manual con permiso; historial con vigencias. |
| Valoración | Costo promedio ponderado (RN-16). |
| Permisos | Matriz de la sección 5. |
| Sin internet | Bloques de numeración por equipo, identificador único por venta, revalidación al sincronizar y conflictos para revisión (RF-OFF-001). |
| Lector | Lector tipo teclado (USB o Bluetooth) que escribe el código y Enter. |
| Migración | Plantilla de tres hojas descargable desde el sistema. |
| Cierre | Día 1 de cada mes a las 02:00, hora de Colombia, del mes anterior; reapertura por gerencia con motivo. |
| Auditoría | Antes y después, usuario, hora, equipo y origen; solo inserción; particiones mensuales. |

## 10. Pendientes de producción (no bloquean el desarrollo)

Son datos que solo la empresa tiene. El sistema quedó configurable para cada uno:

1. Resolución DIAN y proveedor de factura electrónica: se configuran en `consecutivo` y como `sistema_externo`.
2. Sistemas externos concretos (contabilidad, factura electrónica, comercio electrónico): se registran como `sistema_externo` con su URL; el despachador envía los eventos.
3. Volumen real (productos, ventas diarias, usuarios y cajas): recalibra RNF-REN-001 y el tamaño del servidor.
4. Muestra del Excel actual: valida la plantilla antes de la migración real.
