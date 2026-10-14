# Despliegue

## 1. Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Imagen de la aplicación | `Dockerfile` | Compila con Maven en una etapa y ejecuta en `eclipse-temurin:21-jre-alpine` con capas de Spring Boot, usuario sin privilegios y verificación de salud en `/actuator/health/readiness`. |
| Ejecución | `docker-compose.yml` | PostgreSQL 16 con volumen persistente + la aplicación. La aplicación espera a que la base esté sana. |
| Configuración | `.env.ejemplo` | Variables que se copian a `.env` (que nunca se sube al repositorio). |
| Integración y entrega continua | `.github/workflows/ci.yml` | Pruebas en cada PR; imagen en GitHub Container Registry en cada fusión a `main`; despliegue por SSH opcional. |
| Agentes de IA | `.github/workflows/claude.yml`, `revision-ia.yml` | Asistente `@claude`, agente que implementa issues etiquetados `agente-ia` y revisión automática de PR. |

## 2. Variables de entorno

| Variable | Obligatoria | Uso |
|---|---|---|
| `DB_PASSWORD` | Sí | Clave de PostgreSQL (compose la usa para crear la base y para conectar la app). |
| `JWT_SECRET` | Sí | Clave HS256 para firmar las sesiones; mínimo 32 caracteres, distinta en cada ambiente. |
| `ADMIN_PASSWORD` | Primer arranque | Si la base no tiene usuarios, crea `admin` con esta clave. |
| `SPRING_PROFILES_ACTIVE` | No | `default` (base vacía para producción) o `demo` (datos y usuarios de ejemplo). |
| `PUERTO` | No | Puerto publicado en el servidor (8080 por defecto). |
| `DB_URL`, `DB_USER`, `DB_POOL` | No | Solo si la base está fuera de compose. |
| `IMAGEN` | No | Imagen a ejecutar; el despliegue automático la fija al SHA publicado. |

## 3. Ambiente local o de demostración

```bash
cp .env.ejemplo .env            # cambie las claves; SPRING_PROFILES_ACTIVE=demo para ver datos de ejemplo
docker compose up -d --build
docker compose ps               # app debe quedar "healthy"
```

Abra `http://localhost:8080` (usuarios demo con clave `Demo123*`) y la documentación de la API en `http://localhost:8080/swagger-ui.html`.

Verificado en este proyecto el 13-oct-2026: la pila de compose quedó `healthy`, `/actuator/health` respondió `UP`, el inicio de sesión de `gerente1` entregó token y `/actuator/metrics` no queda expuesto.

## 4. Servidor de producción

Requisitos: un servidor Linux con Docker y el plugin compose (2 vCPU y 4 GB de RAM alcanzan para empezar; recalibrar con el volumen real).

1. Cree `/opt/inventario-ferreteria` con `docker-compose.yml` y un `.env` con claves de producción y `SPRING_PROFILES_ACTIVE=default`.
2. Si el paquete de GHCR es privado, inicie sesión una vez: `docker login ghcr.io` con un token de solo lectura de paquetes.
3. Ponga un proxy inverso con HTTPS delante (Caddy o Nginx con Let's Encrypt). La PWA y su modo sin internet necesitan HTTPS fuera de `localhost`.
4. En GitHub cree el ambiente `produccion` (con aprobación obligatoria si se quiere un segundo control), la variable `DESPLIEGUE_HABILITADO=true` y los secretos `SERVIDOR_HOST`, `SERVIDOR_USUARIO` y `SERVIDOR_LLAVE_SSH`.
5. Desde ahí cada fusión a `main`: pruebas → imagen `ghcr.io/<repo>:<sha>` → copia de `docker-compose.yml` → `docker compose pull` y `up -d` → espera a que la app esté sana; si no lo está, el flujo falla y muestra los registros.

Las migraciones de Flyway se aplican solas al arrancar la nueva versión. Como nunca se editan migraciones aplicadas y los cambios de esquema son aditivos, volver a la imagen anterior (`IMAGEN=<sha anterior>` y `docker compose up -d`) es seguro mientras la migración nueva no haya borrado columnas.

## 5. Operación

| Tarea | Recomendación |
|---|---|
| Copias de seguridad (RNF-REC-001: RPO ≤ 15 min) | `pg_dump` diario del esquema `ferreteria` + archivado continuo de WAL (por ejemplo, pgBackRest) a un almacenamiento externo. Probar la restauración cada trimestre. |
| Particiones | La aplicación crea las particiones mensuales del kárdex y la auditoría al arrancar y cada mes, siempre 6 meses adelante. |
| Cierre mensual | Automático el día 1 a las 02:00, hora de Colombia. Revise antes los conflictos de ventas sin internet: el cierre no corre si quedan pendientes del mes. |
| Monitoreo | `/actuator/health/liveness` y `/readiness` para el orquestador o un monitor externo; registros con `docker compose logs app`. |
| Equipos sin internet | Por cada caja nueva: registro en `dispositivo` con `permite_fuera_linea` y su rango de factura en `consecutivo`. |
