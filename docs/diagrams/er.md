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
        enum role
        boolean enabled
        enum accountVisibility
    }
    USER_SETTINGS {
        uuid id PK
        uuid user_id FK "único"
        enum theme
        enum fontSize
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
        enum handedness
        uuid sign_language_id FK
    }
    SIGN_REPORT {
        uuid id PK
        uuid sign_id FK
        uuid user_id FK
        enum reason
        enum status
    }
```

## Contenido / aprendizaje

Cursos, versiones y su jerarquía. `COURSE.sign_language_id` referencia a `SIGN_LANGUAGE` (vista
[Señas](#señas)).

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
        boolean isFree
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
        int order
        uuid course_version_id FK
    }
    LESSON {
        uuid id PK
        string code
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

## Gamificación


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
    }
    ACHIEVEMENT {
        uuid id PK
        enum criteriaType
    }
    USER_ACHIEVEMENT {
        uuid id PK
        uuid user_id FK
        uuid achievement_id FK
    }
    CHALLENGE {
        uuid id PK
        enum type
        enum criteriaType
        enum rewardType
    }
    USER_CHALLENGE {
        uuid id PK
        uuid user_id FK
        uuid challenge_id FK
    }
    SHOP_ITEM {
        uuid id PK
        enum type
    }
    PURCHASE {
        uuid id PK
        uuid user_id FK
        uuid shop_item_id FK
    }
    GIFT {
        uuid id PK
        uuid sender_id FK
        uuid recipient_id FK
        uuid shop_item_id FK
        enum status
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
    }
    NOTIFICATION_TEMPLATE {
        bigint id PK
        enum code
        enum scope
    }
    NOTIFICATION_HISTORY {
        bigint id PK
        uuid user_id FK
        bigint template_id FK
    }
```
