# Inventario Ferretería

Sistema de inventario para una ferretería de una bodega: compras con documento de ingreso, ventas con factura y
lector de códigos de barras, estados disponible / reservada / dañada sin saldos negativos, lotes con vencimiento,
garantías, fracciones y conversiones, costos y precios con historial, cierres mensuales automáticos, migración desde
Excel, integración con sistemas externos, operación sin internet y auditoría de todo movimiento.

Construido con **Java 21 y Spring Boot 3.5**, **PostgreSQL 16** y una **web instalable (PWA)**, con un proceso de
desarrollo asistido por **agentes de IA** que va de la necesidad al despliegue.

## Probarlo en 1 minuto

```bash
cp .env.ejemplo .env      # ponga SPRING_PROFILES_ACTIVE=demo y cambie las claves
docker compose up -d --build
```

Abra http://localhost:8080 e ingrese con `vendedor1`, `compras1`, `inventario1`, `gerente1` o `admin` (clave
`Demo123*`). API documentada en http://localhost:8080/swagger-ui.html.

Sin servidor propio se puede publicar gratis en Render + Neon con el `render.yaml` del repositorio; los pasos están
en [docs/06-despliegue.md](docs/06-despliegue.md#6-demostración-en-la-nube-sin-servidor-propio-render--neon).

Para desarrollar: `mvn verify` (47 pruebas contra PostgreSQL real con Testcontainers; requiere Docker).

## Documentación

| Fase | Documento |
|---|---|
| Requisitos (versión final) | [docs/01-requisitos.md](docs/01-requisitos.md) |
| Análisis y diseño | [docs/02-analisis-diseno.md](docs/02-analisis-diseno.md) |
| Arquitectura y decisiones (ADR) | [docs/03-arquitectura.md](docs/03-arquitectura.md) |
| Base de datos (versión final) | [docs/04-base-de-datos.md](docs/04-base-de-datos.md) |
| Pruebas y trazabilidad | [docs/05-pruebas.md](docs/05-pruebas.md) |
| Despliegue | [docs/06-despliegue.md](docs/06-despliegue.md) |
| Uso de IA en el proyecto | [docs/07-uso-de-ia.md](docs/07-uso-de-ia.md) |

## Estructura

```
src/main/java/co/ferreteria/inventario/
  catalogo/  terceros/  compras/  ventas/  inventario/  precios/  cierres/
  migracion/ integracion/ sincronizacion/ reportes/ auditoria/ seguridad/ comun/
src/main/resources/db/migration/   esquema y datos base (Flyway)
src/main/resources/db/demo/        datos de demostración (perfil demo)
src/main/resources/static/         interfaz web instalable con modo sin internet
src/test/                          pruebas unitarias, de integración y de humo
.claude/                           agentes de IA, comandos y reglas del proyecto
.github/workflows/                 CI/CD y agentes en GitHub
```

## Agentes de IA

`CLAUDE.md` fija las reglas del proyecto. `.claude/agents/` define 8 roles (analista, arquitecto, DBA, backend,
frontend, QA, revisor y DevOps) y `/funcionalidad <necesidad>` los encadena hasta abrir un PR. En GitHub, la etiqueta
`agente-ia` en un issue lanza ese mismo proceso; CI prueba, el agente revisor comenta y una persona aprueba antes de
desplegar. Detalle en [docs/07-uso-de-ia.md](docs/07-uso-de-ia.md).
