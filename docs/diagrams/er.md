# Diagrama Entidad-Relación

Modelo de datos completo: las entidades del sistema y sus relaciones. Se agrupa en vistas
temáticas para facilitar la lectura.

## Usuarios

Perfil, settings, tokens, amistades, los "me gusta" del feed social y la telemetría de uso
(actividad, minutos diarios y sesiones de la app).

Los eventos del feed social **no se almacenan**: se derivan al vuelo de los logros y las señas
aprendidas de cada amigo. Por eso `FRIEND_EVENT_LIKE` no apunta a una tabla de eventos sino a la
fila que originó el evento, mediante el par (`eventType`, `eventRefId`).

```mermaid
erDiagram
    USER ||--|| USER_SETTINGS : tiene
    USER ||--o{ TOKEN : posee
    USER ||--o{ FRIENDSHIP : "requester"
    USER ||--o{ FRIENDSHIP : "addressee"
    USER ||--o{ FRIEND_EVENT_LIKE : "da me gusta"
    USER ||--o{ USER_ACTIVITY : "estuvo activo"
    USER ||--o{ USER_DAILY_ACTIVITY : "suma minutos"
    USER ||--o{ USER_SESSION : "abre la app"

    USER {
        uuid id PK
        string email UK
        string username UK
        string passwordHash "null en cuentas solo-Google"
        string googleSub UK "id estable de la cuenta de Google; null si no hay una vinculada"
        string name
        string lastName
        string avatar "foto de perfil como data URL pequeña; null si no hay"
        enum role
        boolean enabled
        boolean verified
        enum accountVisibility
        set providers "AuthProvider; default LOCAL"
        instant createdAt "alta de la cuenta; métricas de crecimiento"
        uuid current_course_id FK "curso que el usuario eligió como actual; opcional"
    }
    USER_SETTINGS {
        uuid id PK
        uuid user_id FK "único"
        string timezone
        boolean notificationsEnabled
        enum theme
        enum fontSize
        boolean vibrationEnabled
        enum accountVisibility
        int dailyGoalMinutes
        boolean dailyNotificationEnabled
        time dailyNotificationTime
        string profileHeaderColor
    }
    TOKEN {
        bigint id PK
        uuid user_id FK
        string token "hash BCrypt en los códigos de 6 dígitos"
        enum type "EMAIL_VERIFICATION | PASSWORD_RESET | REFRESH"
        instant expiryDate
        int attempts "intentos fallidos; al 5.º el código se descarta"
    }
    FRIENDSHIP {
        bigint id PK
        uuid requester_user_id FK
        uuid addressee_user_id FK
        enum status
        datetime createdAt
        datetime updatedAt
    }
    FRIEND_EVENT_LIKE {
        bigint id PK
        uuid user_id FK "quien da el me gusta"
        enum event_type "FriendEventType: ACHIEVEMENT | SIGN_LEARNED"
        uuid event_ref_id "id de la fila que originó el evento"
        uuid event_owner_id "amigo dueño del evento; destinatario de la notificación"
        instant createdAt
    }
    USER_ACTIVITY {
        uuid id PK
        uuid user_id FK
        instant occurredAt "como mucho una fila por minuto y usuario"
    }
    USER_DAILY_ACTIVITY {
        uuid id PK
        uuid user_id FK
        date activityDate "único junto con user_id"
        int minutes
    }
    USER_SESSION {
        uuid id PK
        uuid user_id FK
        instant startedAt "reloj del cliente"
        int durationSeconds
        uuid clientSessionId "id que genera la app; los latidos actualizan una sola fila"
        instant recordedAt "reloj del servidor"
    }
```

`USER_ACTIVITY` es el registro de «quién estuvo activo»: la API escribe una fila (limitada a una por
minuto y usuario) cuando alguien autenticado hace cualquier pedido, y las métricas de usuarios
activos cuentan usuarios distintos sobre ella. `USER_DAILY_ACTIVITY` guarda los minutos de uso por
día que reporta la app, y `USER_SESSION` cada sesión de primer plano (la app la actualiza con un
latido cada 30 s). Las cuentas `ADMIN` se excluyen de las métricas.

## Señas

Catálogo de señas por lengua y sus reportes. `SIGN_REPORT.user_id` referencia a `USER` (vista
[Usuarios](#usuarios)). `SIGN.animationUrl` guarda el identificador (*key*) del objeto de animación
en el almacenamiento, no una URL. Los clientes piden la URL pública del archivo (con un
cache-buster) a la API, que además busca en el almacenamiento las señas que el catálogo no tiene o
tiene sin archivo (ver [Resolución de la animación de una seña](sequence.md#resolucion-de-la-animacion-de-una-sena)).
La extensión del key dice el formato (`.glb` avatar 3D o `.mp4` video). `SIGN.lateral_animation_url` es el video lateral de una seña en `.mp4` (null en las `.glb`). `SIGN.animation_updated_at` es la
última subida desde el panel de admins (null = archivo original del import) y sirve de cache-buster en
`POST /signs/media`. Los bloques y `USER_LEARNED_SIGN` referencian la seña por su `meaning` (texto), por
eso renombrarla desde el panel reescribe esas filas.

```mermaid
erDiagram
    SIGN_LANGUAGE ||--o{ SIGN : contiene
    SIGN ||--o{ SIGN_REPORT : "es reportada"

    SIGN_LANGUAGE {
        uuid id PK
        string code UK
        string name
        string countryCode
    }
    SIGN {
        uuid id PK
        string meaning UK
        enum handedness
        string animationUrl
        string lateralAnimationUrl
        timestamptz animation_updated_at
        string youtubeUrl
        uuid sign_language_id FK
    }
    SIGN_REPORT {
        uuid id PK
        uuid sign_id FK
        uuid user_id FK
        enum reason
        text description
        enum status
        datetime createdAt
        string adminNote "nota de quien lo moderó"
        uuid resolved_by FK "admin que lo resolvió o rechazó"
        datetime resolvedAt
    }
```

## Contenido / aprendizaje

Cursos, versiones y su jerarquía. `COURSE.sign_language_id` referencia a `SIGN_LANGUAGE` (vista
[Señas](#senas)).

```mermaid
erDiagram
    COURSE ||--o{ COURSE_VERSION : versiona
    COURSE_VERSION ||--o{ TOPIC : agrupa
    TOPIC ||--o{ LESSON : contiene
    LESSON ||--o{ LESSON_BLOCK : contiene

    COURSE {
        uuid id PK
        string code UK
        string name
        text description
        boolean isFree
        decimal price "ARS; solo cursos pagos (viene del YAML)"
        string coverUrl
        string contentHash
        uuid sign_language_id FK
    }
    COURSE_VERSION {
        uuid id PK
        string version
        enum status
        instant publishedAt
        instant updatedAt "última edición desde el panel"
        string updatedBy "email del admin que editó"
        string basedOnVersion "versión de la que se bifurcó el borrador"
        string color "color de la tarjeta en el panel"
        uuid course_id FK
    }
    TOPIC {
        uuid id PK
        string code
        string title
        string subtitle
        text description
        int order
        string coverUrl
        uuid course_version_id FK
    }
    LESSON {
        uuid id PK
        string code
        string name
        text description
        int order
        uuid topic_id FK
    }
    LESSON_BLOCK {
        uuid id PK
        enum type
        int order
        text config
        int xpReward
        uuid lesson_id FK
    }
```

## Progreso / seguimiento

Registro del avance de cada usuario sobre la jerarquía de contenido, más el XP diario y las señas
aprendidas. `USER` es la vista [Usuarios](#usuarios); `COURSE_VERSION`, `TOPIC`, `LESSON` y
`LESSON_BLOCK`, la vista [Contenido / aprendizaje](#contenido-aprendizaje). `USER_LEARNED_SIGN.sign`
guarda la seña como texto (no es FK a `SIGN`).

`PRACTICE_ATTEMPT` registra los intentos de la práctica libre (por tipo de ejercicio, por seña
aprendida o repaso de errores) — mismo shape que `LESSON_BLOCK_ATTEMPT` pero deliberadamente
separada: guardarla **no** cuesta vidas ni avanza `USER_LESSON_PROGRESS`/`USER_TOPIC_PROGRESS`.
Lo único que dispara es el XP del ejercicio, que se otorga al instante al acertar (ver [`PracticeService`](../../src/main/java/com/signasource/signa_api/learning/service/PracticeService.java)).
El repaso de errores lee de ambas tablas: un bloque es un "error pendiente" si su intento más
reciente (en cualquiera de las dos) fue incorrecto. `USER_STATS.lastMistakeReviewAt` marca el último
cobro del XP del repaso de errores: un nuevo cobro exige un error resuelto después de esa fecha.

```mermaid
erDiagram
    USER ||--o{ USER_COURSE_ENROLLMENT : "se inscribe"
    USER ||--o{ USER_TOPIC_PROGRESS : "avanza"
    USER ||--o{ USER_LESSON_PROGRESS : "avanza"
    USER ||--o{ LESSON_BLOCK_ATTEMPT : "intenta"
    USER ||--o{ PRACTICE_ATTEMPT : "practica"
    USER ||--o{ USER_DAILY_XP : "acumula"
    USER ||--o{ USER_LEARNED_SIGN : "aprende"
    COURSE_VERSION ||--o{ USER_COURSE_ENROLLMENT : "referida por"
    COURSE_VERSION ||--o{ USER_LEARNED_SIGN : "referida por"
    TOPIC ||--o{ USER_TOPIC_PROGRESS : "referido por"
    LESSON ||--o{ USER_LESSON_PROGRESS : "referida por"
    LESSON_BLOCK ||--o{ LESSON_BLOCK_ATTEMPT : "referido por"
    LESSON_BLOCK ||--o{ PRACTICE_ATTEMPT : "referido por"

    USER_COURSE_ENROLLMENT {
        uuid id PK
        uuid user_id FK
        uuid course_version_id FK
        uuid organization_id FK "nulo si es autoinscripción"
        enum status
        instant startedAt
        instant completedAt
        instant accessExpiresAt
    }
    USER_TOPIC_PROGRESS {
        uuid id PK
        uuid user_id FK
        uuid topic_id FK
        enum status
        instant startedAt
        instant completedAt
    }
    USER_LESSON_PROGRESS {
        uuid id PK
        uuid user_id FK
        uuid lesson_id FK
        enum status
        instant startedAt
        instant completedAt
        int xpEarned
    }
    LESSON_BLOCK_ATTEMPT {
        uuid id PK
        uuid user_id FK
        uuid lesson_block_id FK
        uuid organization_id FK "nulo si el curso no es de una organización"
        boolean isCorrect
        instant attemptedAt
    }
    PRACTICE_ATTEMPT {
        uuid id PK
        uuid user_id FK
        uuid lesson_block_id FK
        boolean isCorrect
        instant attemptedAt
    }
    USER_DAILY_XP {
        uuid id PK
        uuid user_id FK
        date xpDate
        int xpEarned
    }
    USER_LEARNED_SIGN {
        uuid id PK
        uuid user_id FK
        string sign
        uuid course_version_id FK
        instant learnedAt
    }
```

## Gamificación

`USER_STATS` es el inventario/estado del jugador (gemas, escudos de racha, vidas, multiplicador de XP,
rachas y XP). `ACHIEVEMENT` es el catálogo de logros; `USER_ACHIEVEMENT` registra los que un usuario
desbloqueó (con `earnedAt`). `SHOP_ITEM` es el catálogo de la tienda (multiplicadores de XP, vidas
ilimitadas por tiempo, recargas de vida, escudos de racha y el cofre sorpresa); `PURCHASE` es el
historial de compras (para uno mismo o al comprar un regalo) y `GIFT` el regalo enviado a un amigo,
pendiente de reclamar. `GEM_PACK` es el catálogo de packs de gemas que se compran con dinero real a través de Mercado Pago
(cada uno trae cuánta gema entrega y su precio en pesos); `GEM_ORDER` es cada intento de compra de un
pack: congela gemas y precio, guarda la order de Mercado Pago que la cobra y recién pasa a `PAID`
(y acredita) cuando Mercado Pago confirma el pago. La tabla vieja `gem_purchases` (comprobantes de
Google Play) ya no se usa.

```mermaid
erDiagram
    USER ||--|| USER_STATS : tiene
    USER ||--o{ USER_ACHIEVEMENT : desbloquea
    USER ||--o{ USER_CHALLENGE : progresa
    USER ||--o{ PURCHASE : compra
    USER ||--o{ GIFT : "sender"
    USER ||--o{ GIFT : "recipient"
    ACHIEVEMENT ||--o{ USER_ACHIEVEMENT : "referida por"
    CHALLENGE ||--o{ USER_CHALLENGE : "referido por"
    SHOP_ITEM ||--o{ PURCHASE : "referido por"
    SHOP_ITEM ||--o{ GIFT : "referido por"
    USER ||--o{ GEM_ORDER : "compra con dinero real"
    GEM_PACK ||--o{ GEM_ORDER : "referido por"

    USER_STATS {
        uuid id PK
        uuid user_id FK "único"
        int currentStreak
        int longestStreak
        date lastStreakDate "último día que sumó a la racha (hora de Argentina)"
        date streakCelebratedDate "último día de racha que la app ya festejó"
        date lastSeenDate
        boolean streakShieldPending "un escudo se gastó a medianoche y falta avisarlo"
        int previousWeeklyRank "puesto de la semana anterior; lo fija el reinicio semanal"
        long totalXp
        int weeklyXp
        int gems
        int streakShields
        double xpMultiplier
        instant xpMultiplierExpiresAt
        enum livesMode
        int currentLives
        instant unlimitedLivesExpiresAt
        instant nextLifeAt
        int learnedSignsCount
        instant lastMistakeReviewAt
        instant updatedAt
    }
    ACHIEVEMENT {
        uuid id PK
        string code UK
        string title
        text description
        string iconUrl
        enum criteriaType
        int criteriaValue
        boolean active
        int rewardGems
        int rewardStreakShields
    }
    USER_ACHIEVEMENT {
        uuid id PK
        uuid user_id FK
        uuid achievement_id FK
        instant earnedAt
    }
    CHALLENGE {
        uuid id PK
        string code UK
        string title
        text description
        enum challengeType
        enum criteriaType
        int criteriaValue
        enum rewardType
        int rewardQuantity
        int rewardDurationMinutes
        double rewardMultiplierValue
        boolean active
        int displayOrder
    }
    USER_CHALLENGE {
        uuid id PK
        uuid user_id FK
        uuid challenge_id FK
        date periodStart
        date periodEnd
        int currentProgress
        boolean completed
        instant completedAt
        instant rewardClaimedAt
        instant startedAt
    }
    SHOP_ITEM {
        uuid id PK
        string code UK
        string title
        text description
        enum itemType
        int priceGems
        int quantity
        int durationMinutes
        double multiplierValue
        boolean active
    }
    PURCHASE {
        uuid id PK
        uuid user_id FK
        uuid shop_item_id FK
        int gemsSpent
        instant purchasedAt
        enum status
        instant activatedAt
    }
    GIFT {
        uuid id PK
        uuid sender_id FK
        uuid recipient_id FK
        uuid shop_item_id FK
        text message
        enum status
        instant sentAt
        instant claimedAt
        instant expiresAt
    }
    GEM_PACK {
        uuid id PK
        string productId UK "código estable del pack"
        int gems
        decimal price
        string currency
        int sortOrder
        boolean active
    }
    GEM_ORDER {
        uuid id PK
        uuid user_id FK
        uuid gem_pack_id FK
        int gems
        decimal amount
        string currency
        enum status "PENDING, PAID, REFUNDED"
        string mercadoPagoOrderId UK
        instant createdAt
        instant paidAt
        instant refundedAt "reembolso o contracargo; nulo si no se devolvió"
    }
```

## Compras de usuarios finales y pedidos de reembolso

Además de las gemas (`GEM_ORDER`, vista [Gamificación](#gamificacion)), un usuario puede comprar
**acceso permanente** a un curso pago con un pago único de Mercado Pago: `COURSE_ORDER` es el espejo de
`GEM_ORDER` (precio congelado al crear la orden, `PAID` solo cuando Mercado Pago confirma, `REFUNDED`
si se devuelve). Al pagarse se crea o reactiva una inscripción permanente (sin organización y sin
vencimiento). Convive con la compra por cupos de las organizaciones.

`REFUND_REQUEST` es el pedido que una persona (o un admin de organización) le hace al equipo para que
se le devuelva una compra; sirve para gemas, cursos individuales y cupos. **No devuelve nada por sí
solo**: es una señal que el equipo revisa y ejecuta en Mercado Pago. Como la compra vive en tablas
distintas según el tipo, se la identifica con el par (`purchaseType`, `purchaseId`) y no hay clave
foránea.

```mermaid
erDiagram
    USER ||--o{ COURSE_ORDER : "compra un curso"
    COURSE ||--o{ COURSE_ORDER : "referido por"
    USER ||--o{ REFUND_REQUEST : "pide reembolso"

    COURSE_ORDER {
        uuid id PK
        uuid user_id FK
        uuid course_id FK
        decimal amount "precio congelado"
        string currency
        enum status "PENDING | PAID | REFUNDED"
        string mercadoPagoOrderId
        instant createdAt
        instant paidAt
        instant refundedAt
    }
    REFUND_REQUEST {
        uuid id PK
        string purchaseType "GEMS | COURSE | SEATS"
        uuid purchaseId "sin FK: depende del tipo"
        uuid requested_by_id FK
        enum reason "NOT_RECEIVED | MISTAKE | DUPLICATE | TECHNICAL | NOT_AS_EXPECTED | OTHER"
        string channel "app | web"
        string message
        string note "nota interna del equipo"
        enum status "PENDING | RESOLVED"
        string resolvedByName
        instant resolvedAt
        instant createdAt
    }
```

## Notificaciones

```mermaid
erDiagram
    USER ||--o{ DEVICE_TOKEN : registra
    USER ||--o{ NOTIFICATION_HISTORY : recibe
    NOTIFICATION_TEMPLATE ||--o{ NOTIFICATION_HISTORY : "instancia"

    DEVICE_TOKEN {
        bigint id PK
        uuid user_id FK
        string token
        enum platform
        instant createdAt
        instant lastUsedAt
    }
    NOTIFICATION_TEMPLATE {
        bigint id PK
        enum code UK
        string defaultTitle
        string defaultBody
        enum scope
        boolean schedulable
        boolean enabled
    }
    NOTIFICATION_HISTORY {
        bigint id PK
        uuid user_id FK
        bigint template_id FK
        string title
        string body
        instant sentAt
        instant readAt
        boolean read "col is_read"
        map metadata
    }
```

## Organizaciones

Una organización compra **cupos** por curso (`COURSE_PURCHASE`): cuántos, por cuántos días de acceso
(15 a 60) y a qué precio (precio de lista por cupo = (base + precio por seña × señas del curso) ×
(días / 30)^0,85, menos un descuento por volumen; ver `PurchasePricing`). Se paga con Mercado Pago
Checkout Pro. Un pago aprobado que no puede pagar la compra (cancelada, ya pagada por otro pago o
de otro monto) se devuelve completo y queda en `PURCHASE_REFUND`. Cada compra es independiente: tiene su propio pago, su ventana de invitación (10
días desde el pago) y sus cupos (`PURCHASE_SEAT`), cada uno atado a un email. Al aceptar la
invitación arrancan los días de esa persona (`activatedAt` → `accessExpiresAt`). Los estados
`INVITATION_EXPIRED` y `ACCESS_EXPIRED` no se guardan: se derivan de las fechas. `ORGANIZATION_COURSE`
queda como el registro de cursos que la organización compró alguna vez (lo crea el primer pago) y es
lo que usan las métricas del panel.

Un usuario pertenece a una sola organización: al salir la membresía queda como `REMOVED` (no se
borra), sus inscripciones a cursos pagos pasan a `DROPPED` y sus cupos activos a `REVOKED`; el
progreso se conserva. Si la persona da de baja su cuenta, sus cupos quedan con el email anonimizado (`erased-{id}@invalid`). `INVITE_CODE` solo se usa para invitar administradores al panel (los códigos
compartidos de participantes se eliminaron). Los intentos de ejercicio hechos sobre cursos de la
organización se etiquetan con ella para separar esa actividad de la propia del usuario.

```mermaid
erDiagram
    ORGANIZATION ||--o{ ORGANIZATION_MEMBER : "tiene"
    ORGANIZATION ||--o{ ORGANIZATION_COURSE : "compró"
    ORGANIZATION ||--o{ COURSE_PURCHASE : "compra"
    ORGANIZATION ||--o{ INVITE_CODE : "invita admins"
    ORGANIZATION ||--o{ USER_COURSE_ENROLLMENT : "habilita"
    ORGANIZATION ||--o{ LESSON_BLOCK_ATTEMPT : "contexto de"
    USER ||--o| ORGANIZATION_MEMBER : "pertenece"
    USER ||--o{ PURCHASE_SEAT : "acepta"
    COURSE ||--o{ ORGANIZATION_COURSE : "contratado en"
    COURSE ||--o{ COURSE_PURCHASE : "se compra en"
    COURSE_PURCHASE ||--|{ PURCHASE_SEAT : "tiene"
    COURSE_PURCHASE ||--o{ PURCHASE_REFUND : "devuelve"

    ORGANIZATION {
        uuid id PK
        string name UK
        enum status "PENDING (sin validar) | ACTIVE (validada) | REJECTED | SUSPENDED"
        string cuit UK "11 dígitos, nulo si la creó SIGNA"
        string contactEmail
        string contactPhone
        string suspensionReason "motivo de la suspensión; nulo si no está SUSPENDED"
        instant createdAt
    }
    ORGANIZATION_MEMBER {
        uuid id PK
        uuid organization_id FK
        uuid user_id FK,UK
        enum role "ADMIN | MEMBER"
        enum status "ACTIVE | REMOVED"
        instant joinedAt
        instant removedAt
    }
    ORGANIZATION_COURSE {
        uuid id PK
        uuid organization_id FK
        uuid course_id FK
        instant contractedAt
    }
    COURSE_PURCHASE {
        uuid id PK
        uuid organization_id FK
        uuid course_id FK
        uuid created_by_id FK
        int seats
        int durationDays "15 a 60"
        int signCount "señas al cotizar"
        decimal listPricePerSeat "antes del descuento"
        int discountPercent "por volumen"
        decimal pricePerSeat
        decimal totalAmount
        string currency
        enum status "PENDING_PAYMENT | PAID | CANCELLED | REFUNDED"
        string paymentReference "mercadopago:<id>"
        string mercadoPagoPreferenceId "id de la order de Mercado Pago (nombre heredado)"
        instant paidAt
        instant invitationDeadline "paidAt + 10 días"
        instant refundableUntil "fin del arrepentimiento; solo pagos de Mercado Pago"
        instant refundedAt
        instant cancelledAt
        instant createdAt
    }
    PURCHASE_REFUND {
        uuid id PK
        uuid purchase_id FK
        string paymentId UK "pago de Mercado Pago devuelto"
        decimal amount
        string currency
        enum reason "PURCHASE_CANCELLED | ALREADY_PAID | AMOUNT_MISMATCH | BUYER_REQUESTED | EXTERNAL_REFUND | CHARGEBACK"
        enum status "PENDING | REFUNDED"
        string mercadoPagoRefundId
        string note "motivo del arrepentimiento"
        uuid requested_by_id FK "quien anuló desde el panel"
        instant createdAt
        instant refundedAt
    }
    PURCHASE_SEAT {
        uuid id PK
        uuid purchase_id FK
        string email "único por compra"
        enum status "PENDING_PAYMENT | INVITED | ACTIVE | REVOKED | CANCELLED"
        string invitationToken UK "se renueva al cambiar el email"
        instant invitedAt
        instant lastReminderAt
        instant autoReminderSentAt
        instant activatedAt
        instant accessExpiresAt
        instant revokedAt
        uuid user_id FK "quien aceptó"
    }
    INVITE_CODE {
        uuid id PK
        string code UK
        uuid organization_id FK
        string email
        enum memberRole "ADMIN (los MEMBER son legado y no se canjean)"
        instant expiresAt
        int maxUses
        int useCount
        boolean active
    }
```

## Lista de espera

```mermaid
erDiagram
    WAITLIST_ENTRY {
        long id PK
        string email UK "minúsculas, sin espacios"
        instant createdAt
    }
```

## Importación de contenido

Bitácora de cada corrida del pipeline de importación de contenido (al arrancar o a demanda desde el
panel). No tiene relaciones: es un registro de auditoría independiente.

```mermaid
erDiagram
    CONTENT_IMPORT_RUN {
        uuid id PK
        instant startedAt
        instant finishedAt
        enum trigger "STARTUP | MANUAL"
        enum status "SUCCESS | FAILED"
        string triggeredBy "email del admin; null si fue al arrancar"
        int coursesProcessed
        int created
        int updated
        int unchanged
        text details "líneas por curso, o el error si falló"
    }
```
