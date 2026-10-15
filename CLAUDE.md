# Inventario Ferretería: guía para agentes de IA

Este archivo lo leen Claude Code y los agentes de `.claude/agents/` en cada sesión. Describe cómo se trabaja en
este repositorio. Si una instrucción de una tarea contradice algo de aquí, gana este archivo salvo que la persona
responsable diga lo contrario por escrito en el issue o PR.

## Qué es
Sistema de inventario para una ferretería con una sola bodega: el inventario solo cambia por compras y ventas
(más reservas y daños como cambios de estado), con kárdex auditado, cierres mensuales y operación sin internet.
Requisitos en `docs/01-requisitos.md`; diseño en `docs/02-analisis-diseno.md`; decisiones en `docs/03-arquitectura.md`.

## Pila
Java 21, Spring Boot 3.5, Maven, PostgreSQL 16, Flyway, Spring Security (JWT HS256), JPA para maestros y
JdbcClient para documentos y kárdex, interfaz web (PWA) en React 19 + Vite con íconos Font Awesome en `frontend/` (Maven la compila a `static`).

## Comandos
| Para | Comando |
|---|---|
| Compilar y correr todas las pruebas (exige Docker) | `mvn verify` |
| Una clase de prueba | `mvn test -Dtest=CompraVentaTest` |
| Levantar todo con datos demo | `cp .env.ejemplo .env` (perfil demo) y `docker compose up -d --build` |
| Correr la app contra un PostgreSQL local | `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run` |
| Editar la interfaz con recarga en vivo | `cd frontend && npm install && npm run dev` (abre :5173, API en :8080) |
| Compilar solo el backend | `mvn verify -Dfrontend.omitir=true` |

Usuarios demo: admin, vendedor1, compras1, inventario1, gerente1; clave `Demo123*`.

## Reglas que no se negocian
1. **La base de datos es la última defensa.** Saldos no negativos, períodos cerrados, lotes vencidos, costo promedio y
   auditoría viven en disparadores de `V1__esquema_inventario.sql`. El código Java valida antes para dar buenos
   mensajes, pero nunca reemplaza esas reglas ni las desactiva.
2. **Migraciones inmutables.** Nunca edite un `V*__*.sql` existente: cree `V{n+1}__descripcion.sql`. Un hook lo bloquea.
3. **Toda tabla nueva con datos de negocio** se agrega a la lista de auditoría (`fn_auditar`) en la misma migración.
4. **El inventario solo se mueve con `Kardex.registrar`** (inserta en `movimiento`). Nunca `UPDATE existencia`.
5. **Kárdex y auditoría son de solo inserción.** Para corregir, se anula con un movimiento de reversa.
6. **Toda operación que pueda repetirse por red** (venta, compra, reserva, sincronización) es idempotente por `uuid`.
7. **Permisos**: todo endpoint que modifica datos o expone información sensible lleva `@PreAuthorize` con un permiso
   de la tabla `permiso` (las consultas de catálogo solo exigen sesión); los costos solo se muestran con `COSTO_VER`.
8. **Hora de Colombia** (`America/Bogota`) para períodos, fechas de corte y garantías: use `comun.Fechas`.
9. Nada de secretos en el código ni en los commits: todo por variables de entorno (`.env.ejemplo`).

## Convenciones
- Código, tablas, endpoints y mensajes en español del negocio (factura, ingreso, kárdex, tercero, referencia).
- Paquete por módulo de negocio (`compras`, `ventas`, `inventario`...): controlador delgado, servicio con
  `@Transactional`, consultas SQL con `JdbcClient` y bloques de texto.
- Errores de negocio: `NegocioException` (422) y `NoEncontradoException` (404); `ManejadorErrores` traduce los
  errores de PostgreSQL a 409/422 con `ProblemDetail`.
- Pruebas: unitarias para lógica pura; de integración extendiendo `PruebaIntegracion` (PostgreSQL real con
  Testcontainers y datos demo). Mida diferencias de saldo, no valores absolutos: el contenedor se comparte.
- Commits pequeños en imperativo y en español (`Agrega devolución de compras`). Una rama y un PR por funcionalidad.

## Definición de terminado
- [ ] Requisito trazado (RF-xxx) en el PR y, si cambia el alcance, actualizado en `docs/01-requisitos.md`.
- [ ] Migración nueva (si aplica) con auditoría e índices.
- [ ] Pruebas que fallan sin el cambio y pasan con él; `mvn verify` en verde (cobertura ≥ 65 %).
- [ ] Interfaz actualizada si el usuario la necesita; probada en móvil.
- [ ] Documentación de diseño/arquitectura actualizada si cambió una decisión (nuevo ADR).
- [ ] Revisión del agente `revisor` sin hallazgos críticos y aprobación de una persona antes de fusionar.
