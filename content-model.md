# Modelo de Contenido

Este documento define la estructura de los archivos de contenido utilizados por la aplicación.

Cada archivo representa un único tema. Un tema contiene una o más lecciones, y cada lección contiene una lista ordenada de bloques.

---

# Estructura General

```yaml
topic:
  code: string
  name: string
  description: string

lessons:
  - code: string
    name: string
    description: string

    blocks:
      - type: BLOCK_TYPE
        xp: integer
        config:
          ...
```

---

# Tema (Topic)

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `code` | Sí | Identificador único del tema. |
| `name` | Sí | Nombre visible del tema. |
| `description` | No | Breve descripción del tema. |

---

# Lección (Lesson)

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `code` | Sí | Identificador único dentro del tema. |
| `name` | Sí | Nombre visible de la lección. |
| `description` | No | Breve descripción de la lección. |
| `blocks` | Sí | Lista ordenada de bloques que componen la lección. |

---

# Bloque (Block)

Todos los bloques comparten la siguiente estructura:

```yaml
type: BLOCK_TYPE
xp: integer

config:
  ...
```

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `type` | Sí | Tipo de bloque. |
| `xp` | No | Experiencia otorgada al completar el bloque. Los bloques `INFO` no utilizan este campo. |
| `config` | Sí | Configuración específica según el tipo de bloque. |

---

# Tipos de Bloque

## INFO

Muestra información al usuario. No corresponde a un ejercicio.

```yaml
type: INFO

config:
  text: string
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `text` | Sí | Texto que se mostrará al usuario. |

---

## SELECT_MEANING

Presenta una seña y el usuario debe seleccionar su significado.

```yaml
type: SELECT_MEANING
xp: integer

config:
  sign: string
  options:
    - string
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `sign` | Sí | Identificador de la seña cuya respuesta es correcta. |
| `options` | Sí | Lista de opciones posibles. Debe incluir el valor de `sign`. |

---

## SELECT_SIGN

Presenta una palabra y el usuario debe seleccionar la seña correspondiente.

```yaml
type: SELECT_SIGN
xp: integer

config:
  word: string
  options:
    - string
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `word` | Sí | Palabra cuya respuesta es correcta. |
| `options` | Sí | Lista de opciones posibles. Debe incluir el valor de `word`. |

---

## CONTEXT_RESPONSE

Presenta una pregunta y el usuario debe seleccionar la respuesta correcta.

```yaml
type: CONTEXT_RESPONSE
xp: integer

config:
  question: string
  answer: string
  options:
    - string
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `question` | Sí | Pregunta que se mostrará al usuario. |
| `answer` | Sí | Respuesta correcta. |
| `options` | Sí | Lista de opciones posibles. Debe incluir el valor de `answer`. |

---

## MATCH

Presenta una serie de conceptos que el usuario debe emparejar correctamente.

```yaml
type: MATCH
xp: integer

config:
  concepts:
    - string
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `concepts` | Sí | Lista de conceptos que formarán el ejercicio de emparejamiento. |

---

## VISUAL_RECOGNITION

Presenta una secuencia de señas y un conjunto de opciones para que el usuario seleccione cuáles reconoce dentro de la secuencia. Las opciones pueden incluir señas que sí aparecen en la secuencia y otras que no, como distractores.

```yaml
type: VISUAL_RECOGNITION
xp: integer

config:
  signSequence:
    - string

  options:
    - string

  keepOrder: boolean
```

### Configuración

| Campo | Obligatorio | Descripción |
|--------|-------------|-------------|
| `signSequence` | Sí | Secuencia de señas que se mostrará al usuario. |
| `options` | Sí | Lista de señas posibles, incluyendo señas presentes en la secuencia y otras que no lo están. |
| `keepOrder` | Sí | Indica si las señas seleccionadas deben respetar el orden de la secuencia (`true` o `false`). |

---

# Reglas Generales

- Cada archivo representa exactamente un tema.
- Todo tema debe contener al menos una lección.
- Toda lección debe contener al menos un bloque.
- El orden de las lecciones y de los bloques corresponde al orden en que aparecen en el archivo.
- El campo `xp` debe ser un número entero mayor o igual a cero.
- En los ejercicios de selección (`SELECT_MEANING`, `SELECT_SIGN` y `CONTEXT_RESPONSE`), la lista `options` debe contener la respuesta correcta.
- Los bloques de tipo `INFO` no otorgan experiencia.
- Los tipos de bloque no definidos en este documento se consideran inválidos.