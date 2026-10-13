# Diagramas de estados

Ciclo de vida de las entidades que tienen estados relevantes.

## Cuenta de usuario

```mermaid
stateDiagram-v2
    [*] --> SinVerificar : registro (cuenta activa, correo sin verificar)
    [*] --> Verificada : inicio de sesión externo (Google verifica el correo)
    SinVerificar --> Verificada : verifica el correo / reenvío + verificación
    SinVerificar --> [*] : baja de la cuenta
    Verificada --> [*] : baja de la cuenta
    note right of SinVerificar
        La cuenta nace activa (enabled=true) y puede
        iniciar sesión de inmediato; 'verified=false'
        solo indica que el correo no se verificó aún.
        La baja pone enabled=false y bloquea el login.
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

## Inscripción a un curso

Por cuenta propia sólo se puede inscribir en cursos gratuitos; a los pagos se llega a través de una
organización. Una inscripción dada por una organización puede tener fecha de vencimiento: vencida,
deja de dar acceso aunque siga en `Inscripto`.

```mermaid
stateDiagram-v2
    [*] --> Inscripto : inscribirse (gratuito) o ingresar a una organización
    Inscripto --> Completado : completar el curso
    Inscripto --> Abandonado : la organización lo quita o descontrata el curso
    Abandonado --> Inscripto : vuelve a ingresar a la organización
    Completado --> [*]
    Abandonado --> [*]
```

## Organización

Las que crea SIGNA nacen activas; las que se registran solas desde la web quedan pendientes hasta
que SIGNA las revisa desde su panel: al aprobarlas elige en el mismo paso qué cursos contratan.
Mientras no esté activa, su administrador puede entrar al panel, pero no se pueden generar
invitaciones de participantes ni sumarse participantes.

```mermaid
stateDiagram-v2
    [*] --> Pendiente : alta autogestionada
    [*] --> Activa : alta por SIGNA
    Pendiente --> Activa : SIGNA la aprueba (y contrata cursos)
    Pendiente --> Rechazada : SIGNA la rechaza
    Activa --> Suspendida : SIGNA la suspende
```

## Membresía en una organización

```mermaid
stateDiagram-v2
    [*] --> Activa : canjear código (si hay cupo)
    Activa --> Removida : la organización lo quita, sale por su cuenta o da de baja la cuenta
    Removida --> Activa : canjear un código de nuevo
```

## Progreso de tema y de lección

Mismo ciclo para el avance de un tema y de una lección.

```mermaid
stateDiagram-v2
    [*] --> Bloqueado : inicial
    Bloqueado --> EnProgreso : se empieza
    EnProgreso --> Completado : se termina
    Completado --> [*]
```

## Solicitud de amistad

```mermaid
stateDiagram-v2
    [*] --> Pendiente : enviar solicitud
    Pendiente --> Aceptada : aceptar
    Pendiente --> Rechazada : rechazar
    Pendiente --> Bloqueada : bloquear
    Pendiente --> [*] : cancelar (quien la envió)
    Aceptada --> [*] : dejar de ser amigos
    Aceptada --> Bloqueada : bloquear
    Rechazada --> Pendiente : se vuelve a enviar
    Rechazada --> [*]
    Bloqueada --> [*] : desbloquear
```

Cancelar, dejar de ser amigos y desbloquear **borran** la relación, así que las dos personas
vuelven a partir de cero. Una solicitud rechazada, en cambio, se conserva y se reutiliza si alguien
vuelve a enviarla.

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

## Ítem comprado o regalado

```mermaid
stateDiagram-v2
    [*] --> Pendiente : se regala
    [*] --> EnInventario : se compra un potenciador para uno mismo
    [*] --> Activado : se compra para uno mismo (gemas, vida, escudo, cofre)
    Pendiente --> EnInventario : el destinatario reclama el regalo
    EnInventario --> Activado : se activa el potenciador
    Activado --> [*]
    note right of Pendiente
        Es un regalo aún sin reclamar por el destinatario.
        Sólo el flujo de regalos pasa por este estado.
    end note
    note right of EnInventario
        El ítem ya es del usuario y está en su inventario.
        Los potenciadores temporales (vidas ilimitadas y
        multiplicador de XP) llegan acá para activarse luego;
        el resto de los ítems se activa de inmediato al comprarse.
    end note
    note right of Activado
        Ya se consumió: el efecto fue aplicado.
    end note
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
