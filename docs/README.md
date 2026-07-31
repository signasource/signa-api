# Documentación de `signa-api`

Documentación del backend centrada en los **diagramas** del sistema, pensada para leerse tanto en el
repositorio como en un **sitio renderizable** (MkDocs + Material) con búsqueda y **diagramas
Mermaid**.

El sitio reúne los **diagramas** (cómo se relacionan los datos y los flujos del sistema) y un
**Inicio** con enlaces de referencia (Swagger y notas de release). No documenta el código en detalle.

> El contexto interno para agentes de IA (incluida la guía de mantenimiento de la documentación)
> vive en [`../CLAUDE.md`](../CLAUDE.md), no en el sitio.

## Ver la documentación

### Como sitio (recomendado)

```bash
python -m pip install -r ../requirements-docs.txt
mkdocs serve            # http://127.0.0.1:8000
mkdocs build            # genera build/docs-site/
```

### Como Markdown

Todas las páginas se leen directamente en GitHub/editor; los bloques ```mermaid``` se renderizan de
forma nativa.

## Mapa de contenido

| Sección | Contenido |
|---|---|
| [`index.md`](index.md) | Inicio conciso: enlaces a Swagger, notas de release y diagramas |
| [`diagrams/`](diagrams/) | **Diagramas** de entidad-relación, secuencia y estados |
