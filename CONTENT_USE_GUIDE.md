# Guía para editar el contenido de los cursos

Esta guía para **crear y actualizar el contenido de los cursos**
(temas, lecciones y ejercicios). No hace falta saber programar. Explica dónde viven los archivos,
qué forma tiene cada uno, qué se valida y cómo probar que todo esté bien antes de subirlo.

---

## 1. Ideas básicas

- El contenido se escribe en archivos **YAML**.
- Estos archivos son la única **fuente de la verdad**. La app "importa" esos archivos a la base de
  datos.
- El contenido se trata **como si fuera código compilado**: si un archivo tiene un error (un campo mal
  escrito, algo que falta), la importación **falla entera** y no se guarda nada a medias.

## 2. Dónde viven los archivos

```
src/main/resources/content/
└── LSA/                      ← lengua (Lengua de Señas Argentina)
    └── basic-course/         ← curso
        ├── course.yml        ← datos generales del curso + lista de unidades
        └── topic-01.yml      ← un archivo por cada unidad (con sus lecciones y ejercicios)
```

- **¿Si quiero agregar una nueva lengua?** No se puede únicamente a través de estos archivos. Hay que insertar una nueva fila en la base de datos de manera manual para poder agregar una nueva lengua.
- **¿Si quiero agregar un nuevo curso?** Únicamente creando una nueva carpeta dentro de `/LSA` podés crear un nuevo curso. Es importante que, adentro de `/LSA/nuevo-curso/` se cree un archivo llamado `course.yml` con la metadata del curso.
- **¿Si quiero crear una nueva unidad dentro de un curso?** Hay que crear un archivo yml dentro de `/LSA/nuevo-curso/`. El nombre del archivo no tiene restricciones ni se guarda en la base de datos. Además, hay que agregar la nueva unidad al listado de unidades dentro de `course.yml`

---

## 3. El archivo `course.yml`

Define el curso y enumera los temas que lo componen.

```yaml
course:
  code: basic-course
  name: Curso Básico
  description: Aprende las bases de la Lengua de Señas Argentina.
  free: true
  cover: "mock-cover"

version:
  version: "0.1.0"
  status: PUBLISHED

topics:
  - topic-01.yml
```

### Campos del curso

| Campo | Obligatorio | Qué es |
|---|---|---|
| `code` | Sí | Identificador corto y único. Sin espacios. |
| `name` | Sí | Nombre que ve el usuario. |
| `description` | No | Descripción del curso. |
| `free` | No | `true` (gratis) o `false` (pago). |
| `cover` | No | Referencia a la imagen de portada. |
| `version` | Sí | Número de versión, como texto entre comillas: `"0.1.0"`. |
| `status` | Sí | `DRAFT`, `PUBLISHED` o `ARCHIVED` (ver abajo). |
| `topics` | Sí | Lista de archivos de tema, en el orden en que aparecen. |

A pesar de tener los campos `status` y `version`, no estamos versionando los cursos con el objetivo de mantener la simplicidad para desarrollar las primeras iteraciones. Por el momento, tenemos una única lengua, con un único curso, con una única versión, y cada vez que reciba cambios en el curso, se sobreescribe. Utilizaremos GitHub como herramienta de versionado interna, y se posterga el versionado a nivel base de datos para etapas posteriores.

---

## 4. Un archivo de tema (`topic-01.yml`)

Cada tema tiene sus datos y una lista de **lecciones**. Cada lección tiene una lista de
**bloques** (los ejercicios y pantallas de información).

```yaml
topic:
  code: unidad-1
  name: Cortesía
  description: Saludos básicos

lessons:
  - code: cortesia
    name: Cortesía
    description: Aprende las expresiones básicas de cortesía.
    blocks:
      - type: INFO
        config:
          text: "En esta lección aprenderás las expresiones básicas de cortesía en LSA."

      - type: SELECT_MEANING
        xp: 10
        config:
          sign: hola
          options:
            - hola
            - gracias
            - por_favor
```

### Datos de `topic`

| Campo | Obligatorio | Qué es |
|---|---|---|
| `code` | Sí | Identificador corto y único. |
| `name` | Sí | Nombre visible del tema. |
| `description` | No | Descripción. |
| `cover` | No | Imagen. |

### Datos de cada lección (dentro de `lessons`)

| Campo | Obligatorio | Qué es |
|---|---|---|
| `code` | Sí | Identificador corto y único. |
| `name` | Sí | Nombre visible de la lección. |
| `description` | No | Descripción. |
| `blocks` | Sí | Lista de bloques (al menos uno). |

- El **orden**, tanto de las lecciones dentro del topic o de los bloques dentro de la lección, dependen del orden en el que aparecen en la lista.
- El nombre del archivo es interno: no se guarda en la base de datos ni tiene restricciones.

---

## 5. Los bloques (ejercicios)

Cada bloque tiene:

- `type`: el tipo de bloque (ver la tabla de abajo). Se escribe **en mayúsculas**.
- `xp` (opcional): puntos de experiencia que da el ejercicio. Si no lo ponés, no da puntos. Debe
  ser un número **cero o positivo**.
- `config`: los datos propios de ese tipo de bloque.

Hay **5 tipos de bloque**:

### `INFO` — pantalla de información

Muestra un texto. No es un ejercicio.

```yaml
- type: INFO
  config:
    text: "En esta lección aprenderás las expresiones básicas de cortesía en LSA."
```

| Campo | Regla |
|---|---|
| `text` | Obligatorio, no puede estar vacío. |

### `SELECT_MEANING` — elegir el significado de una seña

Se muestra una seña y el usuario elige qué significa, entre varias opciones.

```yaml
- type: SELECT_MEANING
  xp: 10
  config:
    sign: hola
    options:
      - hola
      - gracias
      - por_favor
```

| Campo | Regla |
|---|---|
| `sign` | Obligatorio. La respuesta correcta. |
| `options` | Obligatorio. Al menos **2** opciones. **Debe incluir el valor de `sign`.** |

### `SELECT_SIGN` — elegir la seña de una palabra

Al revés del anterior: se muestra una palabra y el usuario elige la seña correcta.

```yaml
- type: SELECT_SIGN
  xp: 10
  config:
    word: por_favor
    options:
      - hola
      - perdon
      - por_favor
```

| Campo | Regla |
|---|---|
| `word` | Obligatorio. La respuesta correcta. |
| `options` | Obligatorio. Al menos **2** opciones. **Debe incluir el valor de `word`.** |

### `MATCH` — unir / emparejar

Ejercicio de emparejar conceptos.

```yaml
- type: MATCH
  xp: 15
  config:
    concepts:
      - hola
      - chau
      - gracias
```

| Campo | Regla |
|---|---|
| `concepts` | Obligatorio. Al menos **2** conceptos. |

### `CONTEXT` — completar la frase

Una frase con un hueco (`_`) que el usuario completa eligiendo una opción.

```yaml
- type: CONTEXT
  xp: 15
  config:
    sentence: "¡_ por ayudarme a estudiar LSA!"
    answer: gracias
    options:
      - hola
      - gracias
      - chau
```

| Campo | Regla |
|---|---|
| `sentence` | Obligatorio. La frase (usá `_` donde va el hueco). |
| `answer` | Obligatorio. La respuesta correcta. |
| `options` | Obligatorio. Al menos **2** opciones. **Debe incluir el valor de `answer`.** |

---

## 6. Qué se valida (y qué errores vas a ver)

Cuando se importa un curso, el sistema revisa **todo** y te muestra **todos** los errores juntos
(no solo el primero). Si hay al menos un error, **no se guarda nada**.

Estas son las reglas que se controlan:

**Del curso**
- `code`, `name` y `version` son obligatorios.
- El curso tiene que tener al menos un tema.

**De los temas**
- No puede haber dos temas con el mismo `code` en el curso.
- Cada tema necesita `code` y `name`, y al menos una lección.

**De las lecciones**
- Dentro de un tema, no puede haber dos lecciones con el mismo `code`.
- Cada lección necesita `code` y `name`, y al menos un bloque.

**De los bloques**
- Cada bloque necesita `type` y `config`.
- El `xp`, si está, tiene que ser 0 o positivo.
- Cada tipo de bloque cumple sus propias reglas (las de la tabla de la sección 5): campos
  obligatorios, mínimo de opciones, y que la respuesta correcta esté entre las opciones.

**Además**
- **No se permiten campos desconocidos.** Si escribís mal el nombre de un campo (por ejemplo
  `tex:` en vez de `text:`), la importación **falla**. Esto es a propósito, para que un error de
  tipeo no pase desapercibido.

### Cómo se ven los errores

Los errores indican exactamente dónde está el problema. Por ejemplo:

```
Content validation failed:
  - Topic unidad-1 > Lesson cortesia > Block #2: sign must be one of the options
  - Topic unidad-1 > Lesson cortesia > Block #4: concepts must have at least 2 elements
```

Se lee así: en el **Tema `unidad-1`**, **Lección `cortesia`**, **Bloque N°2**, la seña correcta
no está entre las opciones. Corregís, volvés a probar, y listo.

---

## 7. Cómo probar tu contenido

Después de editar los archivos, conviene probar que importan sin errores **antes** de subirlos.
Hay tres formas, de menor a mayor esfuerzo.

### a) Push

Cada vez que subís cambios (push / pull request), el sistema de CI corre automáticamente una
**validación de todo el contenido** (**no lo importa** a la base de datos, solo lo valida). Si algún YAML tiene un error, el pipeline **falla** y vas a
ver en él la lista de problemas (como en la sección 6).

### b) Docker

Si tenés Docker, con un solo comando se levanta la app, se **valida** y se **importa TODO** el
contenido (todos los cursos bajo `content/`, no uno solo):

```bash
docker compose up --build
```

- Si todo está bien, en los logs vas a ver una línea por curso, p. ej.
  `Imported content LSA/basic-course: CREATED` (o `UPDATED` / `UNCHANGED`, ver más abajo).
- Si algún archivo tiene un error, el arranque **falla** y el detalle aparece en los logs (y no se
  importa nada, ni siquiera los cursos que estaban bien).

### c) Gradle

En caso de tener configurado el entorno localmente, podés importar **todo** el contenido con:

```bash
./gradlew bootRun --args='--import-content'
```

### Reimportar es seguro: solo se actualiza lo que cambió

Podés importar todas las veces que quieras. Cada curso se compara con lo que ya está guardado y se
hace lo mínimo necesario:

| Resultado en el log | Qué pasó |
|---|---|
| `CREATED` | El curso no existía: se carga por primera vez. |
| `UPDATED` | El curso ya existía y **cambió algo**: se actualiza. |
| `UNCHANGED` | El curso ya existía y es **idéntico**: no se toca nada. |

> Cambios que son solo cosméticos en el archivo (espacios, comentarios, reordenar líneas dentro de
> lo permitido) no cuentan como cambio.
