---
description: Lleva una funcionalidad desde la necesidad hasta un PR listo para desplegar, pasando por todos los roles.
argument-hint: <descripción de la necesidad o número de issue>
---
Implemente de punta a punta esta necesidad: $ARGUMENTS

Siga el proceso del equipo en orden. Delegue cada etapa al subagente indicado y pase a la siguiente solo cuando la
anterior haya entregado su resultado. Lleve una lista de tareas visible con el estado de cada etapa.

1. **Requisitos** → `analista-requisitos`: requisitos RF con criterios Dado/Cuando/Entonces en `docs/01-requisitos.md`.
   Si deja `[PREGUNTA]` que bloquea, deténgase y muéstrela; si son supuestos, continúe.
2. **Diseño** → `arquitecto`: contrato REST, transacciones, cambios de datos, secuencia en Mermaid y ADR si aplica.
3. **Datos** → `dba`: migración Flyway nueva con auditoría, índices y pruebas de reglas (solo si hay cambios de datos).
4. **Backend** → `desarrollador-backend`: prueba de integración primero, luego la implementación; `mvn verify` en verde.
5. **Interfaz** → `desarrollador-frontend`: pantalla o ajuste en la PWA (solo si el usuario final la necesita).
6. **Calidad** → `qa`: casos límite, permisos e idempotencia; matriz de trazabilidad actualizada.
7. **Revisión** → `revisor`: si el veredicto es CAMBIOS REQUERIDOS, vuelva a la etapa correspondiente (máximo 2 vueltas).
8. **Entrega** → `devops`: rama, commits, PR con Antes/Después y evidencias. No fusione.

Al final, informe en un párrafo: qué se construyó, requisitos cubiertos, resultado de pruebas y enlace del PR.
