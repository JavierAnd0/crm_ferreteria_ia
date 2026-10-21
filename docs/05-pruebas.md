# Pruebas

## 1. Estrategia

| Nivel | Qué verifica | Herramienta | Dónde |
|---|---|---|---|
| Unitarias | Lógica pura: conversión de unidades, fracciones, precio sugerido, dígito de verificación del NIT, lectura de la cadena `DATABASE_URL` de Neon. | JUnit 5, AssertJ | `src/test/java/.../comun`, `.../terceros` |
| Reglas de base de datos | Lo que PostgreSQL garantiza aunque alguien escriba sin pasar por la aplicación. | Testcontainers (PostgreSQL 16 real) | `basedatos/ReglasBaseDatosTest` |
| Integración (API) | Flujos completos por HTTP con JWT real: compra, venta, reservas, daños, permisos, sin internet, migración y cierres. | Spring Boot Test, MockMvc, Testcontainers | `flujos/*Test`, `catalogo/CatalogoTest` |
| Interfaz de punta a punta | Las 16 pantallas abren sin errores; una venta por código de barras termina en factura; una venta hecha sin red se sincroniza al volver; los detalles y formularios (producto, presentación, unidad, precio manual, compra, tercero, usuario) abren y guardan en ventana emergente; cada error (clave errada, sesión vencida, servidor caído o arrancando) se explica en pantalla. | Playwright + Chromium | `src/test/e2e/humo.mjs`, `sin-internet.mjs`, `productos.mjs`, `ventanas.mjs`, `errores.mjs` |

Decisiones:

* **Base de datos real, no simulada.** Las reglas críticas (saldos, períodos, lotes, auditoría) viven en disparadores; una base en memoria no las ejecutaría. Testcontainers levanta `postgres:16-alpine`, Flyway aplica las migraciones y el perfil `demo` carga los datos de ejemplo.
* **Un contenedor para toda la ejecución.** El contexto de Spring se reutiliza entre clases; por eso las pruebas de flujo miden **diferencias** de saldo (antes y después) y no valores absolutos.
* **Puerta de calidad.** `mvn verify` falla si la cobertura de líneas baja del 65 % (JaCoCo). CI ejecuta lo mismo en cada PR.

## 2. Cómo ejecutarlas

```bash
mvn verify                                   # todas, con reporte de cobertura en target/site/jacoco/index.html
mvn test -Dtest=CompraVentaTest              # una clase
BASE=http://localhost:8080 node src/test/e2e/humo.mjs          # interfaz, con la app en perfil demo
BASE=http://localhost:8080 node src/test/e2e/sin-internet.mjs  # venta sin red y sincronización
BASE=http://localhost:8080 node src/test/e2e/productos.mjs     # detalle y alta de productos en ventana
BASE=http://localhost:8080 node src/test/e2e/ventanas.mjs      # compras, terceros, usuarios, unidades y precios
BASE=http://localhost:8080 node src/test/e2e/errores.mjs       # mensajes de error en el ingreso y sobre ventanas
```

Requisitos: Java 21, Maven y Docker (para Testcontainers).

## 3. Resultado de la última ejecución (21-oct-2026)

| Clase | Pruebas | Resultado |
|---|---|---|
| `CantidadesTest` | 12 | Superadas |
| `DigitoVerificacionTest` | 4 | Superadas |
| `UrlBaseDatosTest` | 4 | Superadas |
| `ReglasBaseDatosTest` | 9 | Superadas |
| `CompraVentaTest` | 7 | Superadas |
| `EstadosYReservasTest` | 4 | Superadas |
| `SeguridadTest` | 9 | Superadas |
| `SincronizacionTest` | 3 | Superadas |
| `MigracionYCierreTest` | 2 | Superadas |
| `CatalogoTest` | 2 | Superadas |
| **Total** | **56** | **56 superadas, 0 fallidas; cobertura de líneas 74 %** |

Interfaz: 16 pantallas abiertas, venta FE-1 emitida por lector y factura abierta, sin errores de consola ni respuestas de error de la API, revisada a 1280 px y 390 px de ancho. Sin internet: con la red cortada en el navegador se emitió FC1-51 desde el bloque de CAJA-01, quedó en la cola local y al volver la red se sincronizó (cola en 0, venta con origen FUERA_DE_LINEA). Ventanas: se creó un producto y otra presentación, se agregaron una unidad y un precio manual, y se abrieron y guardaron la compra, un tercero y un usuario en ventana emergente, sin errores.

## 4. Matriz de trazabilidad requisito → prueba

| Requisito | Criterio verificado | Prueba |
|---|---|---|
| RF-PRO-003 | Conversión a unidad base (bulto, libra) | `CantidadesTest.convierteAUnidadBase`, `CompraVentaTest.ventaConvierteUnidadesUsaLoteFefoYCalculaGarantia` |
| RF-PRO-003 | Fracción mínima | `CantidadesTest.validaMultiploDeFraccionMinima`, `CompraVentaTest.rechazaFraccionQueNoEsMultiploDeLaMinima` |
| RF-PRO-001 / RN-01 | Un código de barras único | `ReglasBaseDatosTest.unCodigoDeBarrasPorReferencia` |
| RF-PRO-001 | Otra presentación para un producto existente; sin permiso responde 403 | `CatalogoTest` (2 pruebas), `src/test/e2e/ventanas.mjs` |
| RF-PRO-001 / RF-PRO-002 | Crear producto y ver su detalle en ventana emergente | `src/test/e2e/productos.mjs` |
| RF-TER-001 / RF-SEG-001 | Editar terceros y usuarios desde la interfaz | `src/test/e2e/ventanas.mjs` |
| RF-TER-001 / RN-14 | Dígito de verificación del NIT | `DigitoVerificacionTest`, fila con error en `MigracionYCierreTest` |
| RF-COM-001 | Documento de ingreso, entrada al inventario, costo y precio | `CompraVentaTest.compraEntraAlInventarioConDocumentoDeIngresoYRecalculaCostoYPrecio` |
| RF-VEN-001 / RN-10 / RN-11 | Factura, lote que vence primero, fecha de fin de garantía | `CompraVentaTest.ventaConvierteUnidadesUsaLoteFefoYCalculaGarantia` |
| RF-VEN-001 / RN-03 | No se vende más de lo disponible | `CompraVentaTest.noPermiteVenderMasDeLoDisponible`, `ReglasBaseDatosTest.noPermiteSaldosNegativos` |
| RF-VEN-001 / RN-06 | Anulación con reverso; idempotencia por uuid | `CompraVentaTest.anularVentaDevuelveLasExistenciasYEsIdempotentePorUuid` |
| RF-PRE-001 | Cambio de precio en la venta exige permiso | `CompraVentaTest.cambiarPrecioEnLaVentaRequierePermiso` |
| RF-PRE-001 / RN-15 / RN-16 | Precio redondeado a 50; costo promedio ponderado | `CantidadesTest.precioRedondeaHaciaArribaAMultiploDe50`, `ReglasBaseDatosTest.precioSeRedondeaHaciaArribaAMultiploDe50`, `ReglasBaseDatosTest.costoPromedioPonderadoConCadaEntrada` |
| RF-EST-001 | Reservar, liberar, consumir y vencer reservas | `EstadosYReservasTest` (3 pruebas de reservas) |
| RF-EST-001 / RN-03 | Lo dañado no se vende y se puede recuperar | `EstadosYReservasTest.loDanadoNoSePuedeVenderYSePuedeRecuperar`, `ReglasBaseDatosTest.noSeVendeLoDanado` |
| RN-10 | Lote obligatorio y lote vencido bloqueado | `ReglasBaseDatosTest.productoConVencimientoExigeLoteYNoSeVendeVencido` |
| RF-CIE-001 / RN-07 | Mes en curso no se cierra; mes cerrado no admite movimientos; saldos del cierre | `MigracionYCierreTest.elMesEnCursoNoSePuedeCerrarYUnMesCerradoNoAdmiteMovimientos`, `ReglasBaseDatosTest.periodoCerradoNoAdmiteMovimientos` |
| RF-SEG-001 / RNF-SEG-001 | 401 sin token, credenciales erradas, permisos del rol, 403 por permiso, costos ocultos | `SeguridadTest` (5 pruebas) |
| RF-SEG-001 | Un token viejo guardado no impide ingresar; todo 401 explica el motivo; la interfaz muestra un mensaje claro para cada error | `SeguridadTest` (3 pruebas), `src/test/e2e/errores.mjs` |
| RF-SEG-001 / RNF-SEG-002 | Alta de usuario auditada con su autor y sin el hash de la clave | `SeguridadTest.crearUsuarioQuedaAuditadoConQuienLoCreoYSinElHashDeLaClave` |
| RF-AUD-001 / RNF-SEG-002 | Auditoría con usuario; kárdex y auditoría inmutables | `CompraVentaTest.cadaMovimientoQuedaAuditadoConUsuario`, `ReglasBaseDatosTest.lasModificacionesQuedanAuditadasConElUsuarioDeLaSesion`, `ReglasBaseDatosTest.elKardexYLaAuditoriaSonInmutables` |
| RF-MIG-001 | Validar sin tocar inventario, cargar válidas, rechazar archivo repetido | `MigracionYCierreTest.migraTercerosProductosYSaldosDesdeLaPlantilla` |
| RF-OFF-001 | Bloque de numeración, reenvío sin duplicar, conflicto sin negativos, equipo no autorizado | `SincronizacionTest` (3 pruebas), `src/test/e2e/sin-internet.mjs` |
| RF-LEC-001 / RNF-INT-001 | Venta por código de barras con Enter | `src/test/e2e/humo.mjs` |
| RF-LEC-002 | Dígito de control GS1, código interno libre y 403 sin permiso | `CodigoBarrasTest`, `CatalogoTest` |

## 5. Lo que estas pruebas no cubren

* **Rendimiento (RNF-REN-001).** No hay prueba de carga. Recomendación: un escenario con Gatling o k6 de 30 usuarios y 20.000 movimientos por día sobre datos de volumen real, cuando la empresa confirme las cifras.
* **Concurrencia real entre cajas.** La serialización por referencia (`SELECT ... FOR UPDATE` en el disparador) está diseñada pero no hay prueba con hilos simultáneos.
* **Despachador de integración.** El envío a un sistema externo y sus reintentos no tienen prueba automática porque no hay sistema externo definido.
* **Recuperación ante desastres (RNF-REC-001).** Requiere la infraestructura de producción (copias y restauración).
