# Diagrama Entidad-Relación

Modelo de datos completo: las entidades del sistema y sus relaciones. Se agrupa en vistas
temáticas para facilitar la lectura.

## Usuarios

Perfil, settings, tokens y amistades.

```mermaid
erDiagram
    USER ||--|| USER_SETTINGS : tiene
    USER ||--o{ TOKEN : posee
    USER ||--o{ FRIENDSHIP : "requester"
    USER ||--o{ FRIENDSHIP : "addressee"

    USER {
        uuid id PK
        string email UK
        string username UK
        string passwordHash
        string name
        string lastName
        enum role
        boolean enabled
        boolean verified
        enum accountVisibility
        set providers "AuthProvider; default LOCAL"
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
        string token
        enum type
        instant expiryDate
    }
    FRIENDSHIP {
        bigint id PK
        uuid requester_user_id FK
        uuid addressee_user_id FK
        enum status
    }
```

## Señas

Catálogo de señas por lengua y sus reportes. `SIGN_REPORT.user_id` referencia a `USER` (vista
[Usuarios](#usuarios)).

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
        string meaning
        text description
        enum handedness
        string animationUrl
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
        string coverUrl
        string contentHash
        uuid sign_language_id FK
    }
    COURSE_VERSION {
        uuid id PK
        string version
        enum status
        instant publishedAt
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

```mermaid
erDiagram
    USER ||--o{ USER_COURSE_ENROLLMENT : "se inscribe"
    USER ||--o{ USER_TOPIC_PROGRESS : "avanza"
    USER ||--o{ USER_LESSON_PROGRESS : "avanza"
    USER ||--o{ LESSON_BLOCK_ATTEMPT : "intenta"
    USER ||--o{ USER_DAILY_XP : "acumula"
    USER ||--o{ USER_LEARNED_SIGN : "aprende"
    COURSE_VERSION ||--o{ USER_COURSE_ENROLLMENT : "referida por"
    COURSE_VERSION ||--o{ USER_LEARNED_SIGN : "referida por"
    TOPIC ||--o{ USER_TOPIC_PROGRESS : "referido por"
    LESSON ||--o{ USER_LESSON_PROGRESS : "referida por"
    LESSON_BLOCK ||--o{ LESSON_BLOCK_ATTEMPT : "referido por"

    USER_COURSE_ENROLLMENT {
        uuid id PK
        uuid user_id FK
        uuid course_version_id FK
        enum status
        instant startedAt
        instant completedAt
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
desbloqueó (con `earnedAt`).

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

    USER_STATS {
        uuid id PK
        uuid user_id FK "único"
        int currentStreak
        int longestStreak
        long totalXp
        int weeklyXp
        int gems
        int streakShields
        double xpMultiplier
        instant xpMultiplierExpiresAt
        enum livesMode
        int currentLives
        instant nextLifeAt
        int learnedSignsCount
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
