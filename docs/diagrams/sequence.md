# Diagramas de secuencia

Flujos dinámicos de los casos de uso principales, descritos a nivel de responsabilidades (no de
componentes concretos del código).

## Registro de usuario

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos
    participant Mail as Servicio de correo

    C->>API: Solicitud de registro
    API->>DB: ¿Correo o usuario ya registrados?
    alt ya existe
        API-->>C: Conflicto (409)
    else disponible
        API->>DB: Crear cuenta (activa, correo sin verificar, contraseña cifrada)
        API->>DB: Generar token de verificación
        API->>Mail: Enviar correo de verificación
        API->>API: Emitir token de acceso
        API->>DB: Generar token de renovación
        API-->>C: Registro exitoso + tokens (sesión iniciada; verificar correo es opcional)
    end
```

## Inicio de sesión

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos

    C->>API: Credenciales
    API->>DB: Verificar credenciales
    alt inválidas o cuenta dada de baja
        API-->>C: No autorizado (401)
    else válidas
        API->>API: Emitir token de acceso
        API->>DB: Generar token de renovación
        API-->>C: Tokens de acceso y renovación
    end
```

## Petición autenticada

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant Auth as Control de acceso
    participant API as API

    C->>Auth: Petición con token de acceso
    alt sin token
        Auth->>API: Continúa como anónimo
        API-->>C: No autorizado si la ruta es protegida (401)
    else con token
        Auth->>Auth: Validar token
        alt válido
            Auth->>API: Continúa autenticado
            API-->>C: Respuesta
        else inválido
            Auth-->>C: No autorizado (401)
        end
    end
```

## Renovación de token (rotación)

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos

    C->>API: Token de renovación
    API->>DB: Buscar token de renovación
    alt no existe o vencido
        API->>DB: Eliminar si estaba vencido
        API-->>C: No autorizado (401)
    else válido
        API->>DB: Invalidar el token usado (rotación)
        API->>API: Emitir nuevo token de acceso
        API->>DB: Generar nuevo token de renovación
        API-->>C: Nuevos tokens
    end
```

## Inicio de sesión con proveedor externo

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant OAuth as Proveedor externo
    participant DB as Base de datos

    C->>API: Credencial del proveedor externo
    API->>OAuth: Verificar identidad
    alt inválida
        API-->>C: No autorizado (401)
    else válida
        API->>DB: Buscar cuenta por correo
        alt existe
            API->>DB: Vincular método externo y habilitar si hacía falta
        else no existe
            API->>DB: Crear cuenta nueva
        end
        API-->>C: Tokens de acceso y renovación
    end
```

## Envío de notificación push

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operación de negocio
    participant DB as Base de datos
    participant Bg as Trabajo en segundo plano
    participant Push as Servicio push

    Op->>DB: Registrar la notificación
    Note over DB: Confirmación (commit)
    DB-)Bg: Disparar envío (asíncrono, tras el commit)
    Bg->>DB: Obtener dispositivos del usuario
    alt sin dispositivos
        Bg-->>Bg: No hay nada que enviar
    else con dispositivos
        Bg->>Push: Enviar mensaje
        Push-->>Bg: Resultado (dispositivos inválidos)
        Bg->>DB: Eliminar dispositivos inválidos
    end
```

## Consulta de logros e inventario

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos

    C->>API: Consultar logros (opcional: filtrar por desbloqueados / activos)
    alt cuenta dada de baja
        API-->>C: No encontrado (404)
    else cuenta activa
        API->>DB: Traer catálogo de logros + los que el usuario desbloqueó
        API->>API: Marcar cada logro como desbloqueado o no y aplicar filtros
        API-->>C: Lista de logros con su estado
    end

    C->>API: Consultar inventario
    alt cuenta dada de baja
        API-->>C: No encontrado (404)
    else cuenta activa
        API->>DB: Traer estado del jugador
        API-->>C: Inventario (gemas, vidas, multiplicador de XP)
    end
```

## Consulta de progreso de cursos

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos

    C->>API: Consultar mi progreso
    API->>DB: Traer inscripciones del usuario (curso y estado)
    alt sin inscripciones
        API-->>C: Lista vacía
    else con inscripciones
        API->>DB: Total de lecciones por tema
        API->>DB: Lecciones completadas por tema
        API->>DB: Tema en progreso de cada curso
        API->>API: Calcular porcentajes del curso y del tema en progreso
        API-->>C: Progreso por curso (lecciones, porcentaje y tema en progreso)
    end
```

## Inscripción a un curso

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos

    C->>API: Inscribirme a una versión de curso
    API->>DB: ¿Ya inscripto en esta versión?
    alt ya inscripto
        API-->>C: Conflicto (409)
    else no inscripto
        API->>DB: Buscar la versión del curso
        alt no existe
            API-->>C: No encontrado (404)
        else existe
            API->>DB: Crear inscripción (estado inscripto)
            API-->>C: Inscripción creada (201)
        end
    end
```

## Interacción con un bloque de lección

Registra el intento del usuario y propaga la finalización hacia arriba en la jerarquía
(lección → tema → curso). El XP y demás recompensas de gamificación se disparan por eventos, fuera
del camino principal. El progreso de lección y tema se crea de forma diferida en la primera
interacción.

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant API as API
    participant DB as Base de datos
    participant Gam as Gamificación (eventos)

    C->>API: Interacción con un bloque (correcto/incorrecto, o vista de bloque informativo)
    API->>DB: Buscar el bloque
    alt no existe
        API-->>C: No encontrado (404)
    else existe
        alt correctitud incompatible con el tipo de bloque
            API-->>C: Entrada inválida (400)
        else válida
            API->>DB: Registrar el intento
            API->>DB: Marcar lección y tema en progreso (si estaban bloqueados)
            alt primer hito (primer acierto / primera vista)
                API-)Gam: XP ganado
                API-)Gam: Señas aprendidas (bloques de ejercicio)
                API->>DB: ¿Todos los bloques de la lección resueltos?
                opt lección completa
                    API->>DB: Completar lección (con XP acumulado)
                    opt todos los temas... completos
                        API->>DB: Completar tema y, si corresponde, la inscripción
                    end
                end
            end
            API-->>C: Intento registrado (201)
        end
    end
```

## Importación de contenido

```mermaid
sequenceDiagram
    autonumber
    participant P as Proceso de importación
    participant F as Archivos de contenido
    participant DB as Base de datos

    P->>F: Leer y validar cada curso
    alt algún curso inválido
        P-->>P: Se detiene, nada se escribe
    else todos válidos
        loop por cada curso
            P->>DB: Comparar con lo guardado
            alt sin cambios
                P-->>P: Sin cambios
            else nuevo o modificado
                P->>DB: Crear o actualizar
            end
        end
    end
```
