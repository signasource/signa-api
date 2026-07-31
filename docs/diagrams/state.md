# Diagramas de estados

Ciclo de vida de las entidades que tienen estados relevantes.

## Cuenta de usuario

```mermaid
stateDiagram-v2
    [*] --> NoVerificada : registro
    NoVerificada --> Verificada : verifica el correo
    NoVerificada --> NoVerificada : reenvío de verificación
    NoVerificada --> Verificada : inicio de sesión externo (auto-habilita)
    Verificada --> [*] : baja de la cuenta
    note right of NoVerificada
        No puede iniciar sesión con credenciales
        hasta verificar el correo.
    end note
```

## Versión de un curso

```mermaid
stateDiagram-v2
    [*] --> Borrador : creada / importada
    Borrador --> Publicada : publicar
    Publicada --> Archivada : archivar
    Archivada --> [*]
    note right of Publicada
        La API sólo sirve la versión publicada
        de cada curso.
    end note
```

## Solicitud de amistad

```mermaid
stateDiagram-v2
    [*] --> Pendiente : enviar solicitud
    Pendiente --> Aceptada : aceptar
    Pendiente --> Rechazada : rechazar
    Pendiente --> Bloqueada : bloquear
    Aceptada --> Bloqueada : bloquear
    Rechazada --> [*]
    Bloqueada --> [*]
```

## Reporte de una seña

```mermaid
stateDiagram-v2
    [*] --> Pendiente : se reporta la seña
    Pendiente --> Revisado : moderación
    Revisado --> Resuelto : se toma acción
    Revisado --> Rechazado : sin acción
    Resuelto --> [*]
    Rechazado --> [*]
```

## Regalo entre usuarios

```mermaid
stateDiagram-v2
    [*] --> Pendiente : se envía el regalo
    Pendiente --> Reclamado : el receptor lo reclama
    Pendiente --> Expirado : vence sin reclamar
    Reclamado --> [*]
    Expirado --> [*]
```

## Resultado de la incorporación de contenido

```mermaid
stateDiagram-v2
    [*] --> Evaluando : se compara con lo guardado
    Evaluando --> Creado : no existía
    Evaluando --> Actualizado : cambió
    Evaluando --> SinCambios : idéntico (no se escribe)
    Creado --> [*]
    Actualizado --> [*]
    SinCambios --> [*]
```
