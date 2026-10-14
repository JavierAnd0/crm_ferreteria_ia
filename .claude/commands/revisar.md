---
description: Revisión completa de la rama actual (o de un PR) con el agente revisor y verificación de pruebas.
argument-hint: [número de PR opcional]
---
1. Si se indicó un PR ($ARGUMENTS), obtenga su rama con `gh pr checkout $ARGUMENTS`.
2. Ejecute `mvn -q verify` y anote el resultado.
3. Delegue al subagente `revisor` la revisión de `git diff origin/main...HEAD`.
4. Presente los hallazgos ordenados por gravedad y el veredicto. Si es un PR, publíquelos como comentario de revisión.
