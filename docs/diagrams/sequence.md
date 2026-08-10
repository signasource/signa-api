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
        API->>DB: Crear cuenta (sin verificar, contraseña cifrada)
        API->>DB: Generar token de verificación
        API->>Mail: Enviar correo de verificación
        API-->>C: Registro exitoso
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
    alt inválidas o cuenta no verificada
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
