# CLAUDE.md

> **Audiencia: inteligencia artificial.** Este archivo es la fuente de contexto de alto nivel
> para agentes que trabajan sobre este repositorio. Es conciso y de alta señal. La documentación
> detallada (también mantenida por IA) vive en [`/docs`](docs/README.md).
>
> **Regla de oro:** cada vez que cambies el código, actualizá la documentación en el mismo
> commit/PR. Ver [Mantener la documentación](#mantener-la-documentación).

---

## 1. Qué es este proyecto

`signa-api` es el backend de **SignaSource**, una plataforma de aprendizaje de lenguas de señas.
Expone una API REST que sirve cursos estructurados, gestiona usuarios y autenticación, gamificación,
notificaciones push y un catálogo de señas.

- **Lenguaje / runtime:** Java 21 (toolchain), Spring Boot 3.5.x
- **Build:** Gradle (wrapper `./gradlew`)
- **Base de datos:** PostgreSQL (prod/local), H2 en memoria (tests)
- **Esquema de la base:** versionado con **Flyway** (`src/main/resources/db/migration/`). Flyway es
  el dueño del esquema; Hibernate corre en `ddl-auto: validate`. Ya **no** hay que hacer
  `docker compose down -v` para cambios estructurales — se escribe una migración. Ver §3 · Versionado de esquema.
- **Paquete raíz:** `com.signasource.signa_api`
- **Contenido:** los cursos son **YAML versionado en el repo** (`src/main/resources/content/`),
  importado a la base por un pipeline idempotente. El YAML es la fuente de verdad, no la DB. El
  import corre al arrancar y también **a demanda desde el panel** (`POST /admin/content/import`),
  y cada corrida queda registrada en `ContentImportRun` (§2 · content).

## 2. Arquitectura en una pantalla

Arquitectura **modular en capas**. Cada módulo de dominio es un paquete de primer nivel bajo
`com.signasource.signa_api` y sigue la misma estructura interna:

```
<módulo>/
  controller/   → REST (@RestController), delgado: valida y delega
  service/      → lógica de negocio (@Service), @Transactional
  repository/   → acceso a datos (Spring Data JPA)
  entity/       → entidades JPA + enums del dominio
  dto/          → records de request/response (nunca se exponen entidades)
  (exception/, event/, config/, util/, validator/ según el módulo)
```

Flujo de una request: `Controller → Service → Repository → DB`. Las entidades **nunca** cruzan
la frontera HTTP; se mapean a/desde DTOs mediante factories estáticas (`Dto.from(entity)`).

**Anatomía de una feature** (camino feliz de punta a punta; cada paso respeta las convenciones de §3):

1. `entity/` — entidad JPA (+ enum del dominio si aplica).
2. `repository/` — `JpaRepository` con métodos derivados / `@Query` / `@EntityGraph` según lo que se necesite.
3. `dto/` — records de request (validación Jakarta) y response (factory `from(entity)`).
4. `service/` — lógica + `@Transactional`; mapea entidad↔DTO y lanza excepciones de dominio.
5. `controller/` — delgado: recibe/valida el DTO, saca el usuario de `@AuthenticationPrincipal`, delega
   y devuelve `ResponseEntity<Dto>` con el status correcto.
6. **Tests** de service y controller (§3 · JUnit 5 + Mockito), cobertura ≥ 90%.
7. **Doc en el mismo PR** (§9): diagramas en `/docs` si cambió el modelo o un flujo; `CLAUDE.md` si
   tocaste una convención o invariante.

**Módulos de dominio**:

| Módulo | Responsabilidad |
|---|---|
| `auth` | Registro (auto-login + verificación de email por flag `verified`), login, JWT, refresh, reset de password, Google OAuth2 |
| `users` | Perfil propio y **perfil público de otra persona** (`PublicProfileService`: compone stats, XP semanal, logros y cursos; respeta `AccountVisibility`), settings, búsqueda de usuarios (`GET /users/search`, resuelve relación + amigos en común), amistades (`Friendship`: request/accept/reject/cancel/remove/block/unblock), feed de actividad de amigos y sus "me gusta" (`FriendEventLike`), username, daily goal, color de header |
| `learning` | Cursos, versiones, temas, lecciones, bloques, señas y reportes de señas; seguimiento de progreso (inscripciones, progreso de tema/lección, intentos de bloque); animación de seña vía presigned URL de R2 (`GET /signs/{meaning}/animation`; `meaning` es único) y su variante batch (`POST /signs/animations`, usada por el lesson player para precargar todas las animaciones de una lección en un solo request). **Tipos de bloque:** `INFO` (contenido textual, opcionalmente con `myths`), `INTRODUCE_SIGN` (presenta una seña nueva; campos `meaning` + `word`), `SELECT_MEANING`, `SELECT_SIGN`, `CONTEXT_RESPONSE`, `MATCH`, `VISUAL_RECOGNITION`, `INVISIBLE_SIGNS` (no se muestra al usuario; campo `signs`: lista de meanings que se insertan en el catálogo de señas durante el import — útil para precargar señas de lecciones que no usan `INTRODUCE_SIGN`). **Práctica libre** (`PracticeController`/`PracticeService`, `/practice/*`): sesiones sueltas de repaso por tipo de ejercicio, por seña aprendida (`UserLearnedSign`) o de errores pendientes, reutilizando los `LessonBlock` evaluables (no `INFO`/`INTRODUCE_SIGN`/`INVISIBLE_SIGNS`/`PERFORM_SIGN`/`SPELL_NAME`) de las lecciones ya empezadas de los cursos en los que el usuario está inscripto. Los intentos se guardan en `PracticeAttempt` — deliberadamente separada de `LessonBlockAttempt`: no dispara XP, vidas ni progreso de lección/tema/curso. El repaso de errores combina ambas tablas (ver [docs/diagrams/sequence.md](docs/diagrams/sequence.md#practica-libre)). Excepción: completar un lote de repaso de errores (`POST /practice/mistakes/complete`) puede otorgar una recompensa de XP fija (`PracticeService.completeMistakeReview`, publica `XpEarnedEvent`) — solo si desde el último cobro (`UserStats.lastMistakeReviewAt`) el usuario resolvió un error; si no, devuelve 0 |
| `content` | Pipeline de importación de contenido YAML (load → validate → persist), idempotente; además **upserta el catálogo de señas** a partir de los bloques: crea una `Sign` por cada _meaning_ que un bloque renderiza como animación, con `animationUrl = lsa/{meaning}.glb` y `handedness = ONE_HANDED` (`SignCatalogExtractor` + `SignCatalogImporter`). **Historial de imports:** `ContentImportHistoryService` corre el pipeline (al arrancar vía `ContentImportRunner`, o a demanda desde el panel) y registra cada corrida en `ContentImportRun` (trigger `STARTUP`/`MANUAL`, status `SUCCESS`/`FAILED`, contadores created/updated/unchanged, `details`). **Las fallas se registran y loguean, nunca se relanzan:** un commit de contenido roto no tira abajo la API al arrancar; el panel muestra el `FAILED`. Expuesto en `admin`: `POST /admin/content/import` (dispara, devuelve la corrida) y `GET /admin/content/import-runs` (historial paginado), solo `ADMIN` |
| `gamification` | Stats de usuario, logros, desafíos, tienda, compras, regalos; XP diario/semanal, señas aprendidas y mecánica de vidas/racha. **El catálogo de la tienda se siembra en un único lugar:** `resources/db/seed/store_items.sql` (lo corre `common/DataLoader`), que hace upsert por `code` — así editar un precio o un texto se aplica al reiniciar. No agregues un `CommandLineRunner` que siembre `shop_items`: hubo uno y duplicaba el catálogo. Los textos de ese seed son user-facing, así que van **en español** (única excepción a la regla de §3). **Packs de gemas con dinero real** vía Google Play (`GemPack` = producto in-app de Play + cantidad de gemas, sembrado por `GemPackSeeder`; `GET /store/gem-packs`). El cliente compra en Play y manda `{productId, purchaseToken}` a `POST /store/gem-purchases`: `GemPurchaseService` verifica el token contra la Google Play Developer API (`GooglePlayPurchaseVerifier`, fuera de la transacción) y `GemCreditService` acredita las gemas y persiste `GemPurchase` con `purchaseToken` **único** (idempotente: repetir el token del mismo usuario devuelve `alreadyGranted=true`; de otro usuario o ya consumido → 409). Los ítems `GEMS` del catálogo con precio en gemas quedan inactivos (`store_items.sql`). Las gemas **nunca** se acreditan sin verificación del servidor. **Desafíos** (`ChallengeService`, `GET /challenges` + `POST /challenges/{id}/claim`, seed `challenges.sql`): tres sets, `FIRST_STEPS` ("Primeros pasos", único), `DAILY` (una fila `UserChallenge` por día argentino) y `WEEKLY` (una por semana lunes–domingo argentina, `weeklyResetsAt`), todas creadas perezosamente; `daily` y `weekly` no se devuelven hasta que todos los primeros pasos estén reclamados. El progreso lo reportan los puntos donde ya ocurre la actividad vía `ChallengeService.record` (lección completada y camara en `CourseTrackingService`, camara en `PracticeService.recordAttempt`, XP y racha en `UserStatsEventListener`, solicitud de amistad en `FriendshipService`) y **no toca `UserStats`**: la recompensa (gemas, multiplicador de XP, vida, escudo) se acredita recién al reclamar. Una recompensa diaria/semanal no reclamada **vence con su período** (`claim` de una fila con `periodEnd` pasado → 400); los primeros pasos no vencen. El multiplicador de XP activo (`UserStats.getEffectiveXpMultiplier`, por desafío o por la tienda) se aplica en `UserStatsEventListener.handleXpEarnedEvent` a **todo** XP ganado (total, semanal, diario y desafíos `EARN_XP`). Criterios absolutos (`isAbsolute`, p. ej. racha) toman el total; el resto suma. Las filas de primeros pasos se siembran con el historial del usuario |
| `organizations` | Modelo organizacional (capacitación de empresas/instituciones). Una `Organization` **contrata cursos completos** (`OrganizationCourse`) y todos sus miembros activos acceden al mismo conjunto; el progreso es individual. Un usuario pertenece a **una sola** org (`OrganizationMember`, único por `user_id`; `MemberRole` ADMIN/MEMBER, `MemberStatus` ACTIVE/REMOVED — quitar a alguien marca REMOVED y no borra su progreso). Se ingresa con `InviteCode` (código compartido, con `maxUses`/`expiresAt`, o **invitación por email**: código de un solo uso con `email`, enviado por `EmailService`); canjearlo (`POST /organizations/invite-codes/redeem`) crea/reactiva la membresía y `OrganizationEnrollmentService` inscribe al usuario en todos los cursos contratados (`UserCourseEnrollment.organization`). Contratar un curso después inscribe a los miembros activos; descontratarlo o quitar un miembro pasa las inscripciones a `DROPPED` y limpia `currentCourse` si apuntaba ahí — salvo en cursos **gratuitos**, que sólo se desvinculan de la org (`organization = null`, sin vencimiento) para no dejar al usuario afuera de algo abierto a todos. `UserCourseEnrollment.grantsAccess(now)` (no `DROPPED` y acceso no vencido) es el único chequeo de acceso: lo usan `activeEnrollments`, `LessonService.getLessonContent` y `recordBlockInteraction`. **A los cursos pagos sólo se llega por una org:** la autoinscripción (`POST /courses/{versionId}/enroll`, `joinFreeCourse`) exige `Course.isFree`. **Ciclo de vida de la org** (`OrganizationStatus`): `POST /organizations` (SIGNA) la crea `ACTIVE`; el **alta autogestionada** `POST /organizations/register` (pública, CUIT normalizado a 11 dígitos, email en minúsculas) crea la cuenta `ORG_ADMIN` + la org `PENDING`; mientras no esté `ACTIVE` (`PENDING`, `REJECTED`, `SUSPENDED`) no se pueden crear códigos/invitaciones de participantes ni canjearlos (los de admin sí). `memberLimit` (nulo = sin límite) es el cupo de participantes `MEMBER` activos, validado al canjear. **Back-office SIGNA (`ADMIN`):** listar, ver y cambiar el estado de las orgs vive en el módulo `admin` (`/admin/organizations`, `AdminService.updateOrganizationStatus`: `PENDING → ACTIVE` contrata en el mismo paso los `courseIds` elegidos; `REJECTED`/`SUSPENDED`). En `organizations` quedan `PATCH /organizations/{id}` (renombrar y cupo — no por debajo de los activos; el estado no se toca acá) y `DELETE /organizations/{id}/admins/{userId}` (membresía `REMOVED` y `ORG_ADMIN` → `USER`). `GET /organizations/{id}` y `GET .../admins` también para el `ORG_ADMIN` de esa org. `GET /organizations/pricing` (público) expone `organizations.price-per-person`. **Salidas:** `DELETE /organizations/me` deja la org (solo `MEMBER`; un admin no puede auto-quitarse) (la baja de cuenta borra la membresía y las inscripciones vía `AccountErasureRepository`). Cada `LessonBlockAttempt` de un curso de la org se etiqueta con `organization` para distinguir la actividad organizacional de la propia. **Roles:** `ADMIN` de SIGNA crea orgs, asigna admins (`POST /organizations/{id}/admins` → `Role.ORG_ADMIN` + membresía ADMIN) y contrata cursos; `ORG_ADMIN` gestiona **solo su org** (códigos, invitaciones, quitar miembros, ver admins) — el alcance lo valida `OrganizationAccessService.requireManage` (403 vía `ForbiddenException`) además de `SecurityConfig`. `GET /organizations/me` le dice a la app a qué org pertenece el usuario (incluye `status`, para que el panel muestre "pendiente de aprobación"). **Admins del panel:** `POST /organizations/{id}/admin-invitations` (solo `ADMIN` de SIGNA) crea un `InviteCode` con `memberRole = ADMIN` atado a un email y manda un link al panel (`app.panel-url` + `/accept-invite?code=...`); la persona se registra o inicia sesión en el panel y canjea el código con el mismo `POST /organizations/invite-codes/redeem`, que la hace `ADMIN` de la org y `Role.ORG_ADMIN` sin inscribirla en cursos (un admin que canjea un código de participante no se degrada). **Panel de seguimiento (solo lectura, mismo alcance de `requireManage`):** `GET /organizations/{id}/members` (filtros `query`/`status`/`activity`, orden `sort=name|progress|lastActivity|joinedAt,asc|desc`; progreso + última actividad + módulo actual + `activityStatus`), `GET .../members/{userId}` (progreso individual), `GET .../overview` (participación / progreso / desempeño, con evolución semanal), `GET .../modules` (desempeño por tema) y `GET .../activity` (actividad día por día en hora de Argentina; `totals.activeParticipants` son personas distintas en el período; sin fechas arranca el día en que se creó la org). **Período:** los cinco (overview, activity, modules, members, members/{userId}) aceptan `from`/`to` opcionales (`LocalDate` ISO, inclusivos, ambos o ninguno, ≤ 366 días → 400 vía `MetricsPeriod.of`); sin fechas = todo el historial. `MetricsPeriod` es la única definición: lo que *pasó* (intentos, % correctas, lecciones/módulos completados, minutos, días activos, quién estuvo activo) cuenta **dentro** del período; los *estados* (progreso, quién terminó, módulo actual, en curso/completado por módulo, quién ya se había unido) se miden **al cierre** del período (`< end`); las rachas son siempre las actuales. Las consultas de `OrganizationMetricsRepository` reciben siempre límites concretos (sin parámetros nulos: el historial completo usa 2000-01-01 → ahora). Overview, modules, members y activity aceptan `courseId` para medir contra un solo curso contratado (`OrganizationContentScope.resolve(orgId, courseId)`, 404 si no está contratado); los intentos se filtran por **curso** (no por versión) para que cuenten los hechos sobre versiones anteriores. `ContentScope.courseIds()/versionIds()` nunca vuelven vacíos (un id que no matchea nada) para no generar `IN ()`. El listado de participantes calcula progreso y estado **antes** de ordenar y paginar en memoria (`OrganizationMemberQueryService`): el orden y el filtro por estado dependen de métricas calculadas, y el roster está acotado por el cupo. `MemberActivityStatus` (ACTIVE/INACTIVE/NOT_STARTED/COMPLETED/REMOVED) es la única fuente del estado que muestra el panel y usa la misma ventana que el overview (`MetricsPeriod.activeSince()`: el período, o los últimos 7 días sin período). Las consultas agregadas viven en `OrganizationMetricsRepository` (`Repository<>` con `@Query` JPQL — ojo: un `@EntityGraph` ahí se resolvería contra `LessonBlockAttempt`, usá `JOIN FETCH`) y están verificadas contra H2 en `OrganizationMetricsRepositoryTest`. Los participantes son los miembros `MEMBER` activos (los admins no cuentan); "activo" = algún intento sobre contenido contratado en el período (sin período, en los últimos 7 días). El progreso se mide contra la versión publicada de los cursos contratados. Minutos de aprendizaje y días activos salen de `UserDailyActivity`, que **no** distingue organización |
| `waitlist` | Lista de espera de la web (`POST /waitlist`, público): guarda el email normalizado (minúsculas, sin espacios) en `WaitlistEntry` con índice único. Responde **202 siempre**, exista o no el email, para no revelar quién está anotado; un duplicado simultáneo lo resuelve el índice único |
| `notification` | Tokens de dispositivo, plantillas, historial y envío push vía Firebase (FCM). El historial se expone como bandeja de entrada (`NotificationController`: listar, no leídas, contador, marcar leída/todas); **el lado de escritura es interno**: las notificaciones las producen los servicios que las disparan |
| `config` | Seguridad, rate limiting, Jackson, Google, MVC |
| `exceptions` | Excepciones de dominio + `@RestControllerAdvice` global |
| `common`, `validation` | Utilidades transversales (converters JPA, validación de password) |

## 3. Convenciones que SIEMPRE se mantienen

Respetalas al escribir código nuevo — son invariantes del proyecto, no sugerencias.

### Invariantes duras (no se rompen)

Resumen de alta prioridad; el detalle y los ejemplos están en los bloques siguientes.

1. **DI por constructor** con campos `private final` + `@RequiredArgsConstructor`. Nunca `@Autowired` en campos.
2. **Las entidades NUNCA cruzan la frontera HTTP.** Request/response son `record` DTO; mapeo con `from(entity)`.
3. **Controllers delgados:** sin lógica, devuelven `ResponseEntity<Dto>` y toman el usuario de
   `@AuthenticationPrincipal`. La lógica y las `@Transactional` viven en `@Service`.
4. **Errores:** lanzá excepciones de dominio; el HTTP lo arma `GlobalExceptionHandler`. Mensajes en inglés.
5. **Todo en inglés:** código, nombres, comentarios y mensajes. Comentarios mínimos y sobre el *por qué*.
6. **Código con lógica ⇒ tests** (JUnit 5 + Mockito, cobertura ≥ 90%).
7. **No rompas:** la idempotencia del importador, el hasheo BCrypt de passwords, ni expongas secretos.
   El bypass de seguridad del perfil `local` jamás va a producción.
8. **Antes de pushear:** `./gradlew spotlessApply` y los checks en verde (§6).

**Inyección de dependencias**
- Constructor injection vía campos `private final` + `@RequiredArgsConstructor` de Lombok.
- Nunca `@Autowired` en campos.

**Capas**
- Controllers delgados: sin lógica de negocio. Devuelven `ResponseEntity<Dto>`.
  - **Usuario autenticado:** obtenelo con `@AuthenticationPrincipal CustomUserDetails`
    (`userDetails.getUser()`), no lo vuelvas a buscar por id. El principal ya pasó por `JwtAuthFilter`:
    no re-valides su existencia (sí validá los ids que llegan en el request, p. ej. el destinatario de una acción).
  - **Status semánticos:** `201` en creaciones, `200` en lecturas/updates; update parcial → `PATCH`, no `PUT`.
    Los conflictos y errores salen como excepción de dominio (ver **Errores** en esta sección), no armes el status a mano.
  - **Sin prefijo de ruta hardcodeado** (`/api/v1`, …) en el controller. Hoy no se usa ninguno; si se
    agregara, iría como propiedad de la app, no repetido en cada `@RequestMapping`.
- La lógica y las transacciones viven en `@Service`. Usá `@Transactional(readOnly = true)` en lecturas.
- Los repositorios extienden `JpaRepository` y declaran métodos derivados / `@Query`.

**DTOs**
- Son `record`. Request DTOs con validación Jakarta (`@NotNull`, `@Email`, `@ValidPassword`, …).
- Response DTOs con factory estática `from(...)`. No devolver entidades JPA.
- **El snake_case del JSON lo maneja el `ObjectMapper` global** (`config/JacksonConfig`,
  `PropertyNamingStrategies.SNAKE_CASE`). No anotes campos con `@JsonProperty` sólo para el snake_case:
  es redundante.

**Entidades JPA**
- `@Data @Entity @Builder @NoArgsConstructor @AllArgsConstructor`. IDs `UUID` con
  `@GeneratedValue(strategy = GenerationType.UUID)`.
- Relaciones `@ManyToOne(fetch = FetchType.LAZY)` por defecto (evitar N+1 y fetch EAGER salvo
  necesidad real). En colecciones `@OneToMany` usar `@ToString.Exclude` y `@EqualsAndHashCode.Exclude`
  para evitar recursión/carga accidental.
- Enums persistidos con `@Enumerated(EnumType.STRING)` (nunca ORDINAL). Fijales `@Column(length = ...)`:
  sin eso Hibernate crea un `VARCHAR(255)` para guardar valores de ~10 caracteres.
- **No pongas `@Column(name = "...")` sólo para snake_case:** Hibernate ya mapea `camelCase` → `snake_case`
  por defecto. Usá `name` únicamente cuando el nombre real difiere de esa convención o hay que **escapar
  una palabra reservada de SQL** (p. ej. `order` → `@Column(name = "\"order\"")`, ver `Topic`).

**Errores**
- Lanzar excepciones de dominio de `exceptions/` (`NotFoundException`, `InvalidInputException`,
  `ResourceAlreadyInUseException`, `InvalidCredentialsException`, `InvalidTokenException`).
- El mapeo a HTTP lo hace `GlobalExceptionHandler`. La respuesta de error siempre es
  `ErrorResponse.of(message, status)`. No armar respuestas de error ad-hoc en los controllers.
- **Mensajes de excepción en inglés**, como el resto del código (ver **Comentarios**).

**Nombres / formato**
- `google-java-format` estilo **AOSP** (indentación de 4 espacios), impuesto por Spotless. Spotless
  además borra imports sin usar (`removeUnusedImports`), normaliza anotaciones (`formatAnnotations`)
  y formatea los `*.md` (`trimTrailingWhitespace`, `endWithNewline`).
- **Prohibidos los wildcard imports:** una regla custom de Spotless (`custom('No wildcard imports')`)
  lanza `AssertionError` si detecta `import x.*;`. También: sin imports sin usar.

**Comentarios**
- **Siempre en inglés**, igual que el código y los nombres (no hay comentarios en español en `src/`).
- **Mínimos y sólo donde aportan:** explicá el *por qué* de una decisión no obvia, no el *qué* (el
  código ya lo dice). Preferí Javadoc `/** … */` a nivel de clase/método cuando el comportamiento no
  es evidente (ver `NotificationPushListener`, `ContentImportService`).
- La documentación narrativa en español (`CLAUDE.md`, `/docs`, `README`) es otra cosa: eso sí va en
  español, como el repo.

**Tests unitarios**
- **JUnit 5 + Mockito puro, sin levantar Spring:** `@ExtendWith(MockitoExtension.class)`, los
  colaboradores con `@Mock` y la clase bajo prueba con `@InjectMocks`. **No** usar `@SpringBootTest`
  para tests unitarios (queda para casos de integración reales, como `ContentValidationCheckTest`).
- **Los controllers se testean llamando al método directamente** y verificando el `ResponseEntity`
  (status y body); el service se mockea. No se usa `MockMvc`.
- Assertions y excepciones con **JUnit** (`assertEquals`, `assertThrows`, …), no AssertJ. Verificá
  interacciones con `verify(...)` / `verifyNoInteractions(...)`.
- Fixtures compartidas en `@BeforeEach setUp()`, construidas con los `@Builder` de las entidades.
- **Nombres descriptivos en estilo `should…`** (p. ej. `shouldThrowNotFoundWhenCourseHasNoPublishedVersion`).
  Es el estándar; evitá el prefijo `test…`.
- Lo que toca DB usa **H2 en memoria**. Todo código nuevo con lógica viene con tests (cobertura ≥ 90%, §6).

**Idempotencia del contenido**
- El importador calcula un `contentHash` SHA-256 y produce `CREATED / UPDATED / UNCHANGED`.
  Cualquier cambio en el pipeline debe preservar esta idempotencia (ver `content/service/*`).

**Versionado de esquema (Flyway)**
- El esquema lo versiona **Flyway**; las migraciones viven en `src/main/resources/db/migration/`
  con el naming `V<n>__descripcion.sql` (p. ej. `V2__content_import_runs.sql`). `V1__baseline.sql`
  es un snapshot del esquema de prod al adoptar Flyway (generado con `pg_dump --schema-only`).
- `baseline-on-migrate: true` + `baseline-version: 1`: en bases **existentes** (prod) Flyway marca
  V1 como baseline y **no lo ejecuta**; solo corre V2 en adelante. En bases **vacías** (local nuevo,
  CI con Postgres) corre V1 y todo lo demás, construyendo el esquema desde cero.
- Hibernate corre en `ddl-auto: validate` (prod/local/default): **no** crea ni altera tablas, solo
  valida que las entidades matcheen el esquema. **Agregar/cambiar una entidad ⇒ escribir una migración
  en el mismo PR**, o `validate` falla al arrancar. Confirmá que el tipo de columna coincide con cómo
  Hibernate mapea el campo (p. ej. `Instant` → `timestamp(6) with time zone`).
- **Tests:** H2 con `ddl-auto: create-drop` y `spring.flyway.enabled: false` (las migraciones son
  específicas de Postgres). No escribas migraciones pensando en H2.
- Nunca edites una migración ya aplicada: agregá una nueva. Nunca se taggea/baseline a mano en prod.

**Gotchas / lecciones de code review** (recurrentes en las PRs del repo — evitalas de entrada)
- **N+1:** si vas a necesitar relaciones, traelas en una sola query con
  `@EntityGraph(attributePaths = {...})` en el método del repositorio (ver `CourseVersionRepository`,
  `LessonRepository`), en vez de encadenar varias consultas.
- **`enabled` vs `verified`:** `enabled` marca **solo** si la cuenta está activa; la baja de cuenta
  (`deleteAccount`) pone `enabled = false` y Spring (`DaoAuthenticationProvider`) bloquea su login.
  El estado del correo va en `verified` (registro nace `enabled=true, verified=false`; `verifyAccount`
  lo pone `true`). Una cuenta sin verificar **sí** puede iniciar sesión.
- **Baja de cuenta** (`UserService.deleteAccount`, `DELETE /users/me`): borra los tokens, los device
  tokens y **todos los datos personales y de actividad** (`AccountErasureRepository`: progreso, intentos,
  stats, logros, desafíos, amistades, likes, notificaciones, settings, membresía de org y los
  `InviteCode` atados al email del usuario, que no tienen FK y se buscan por email antes de anonimizarlo) y **anonimiza** la
  fila de `users` (`enabled = false`, email/username `deleted_<id>`, nombre "Usuario eliminado", hash
  inválido, visibilidad `PRIVATE`). La fila queda solo para que `Purchase`, `GemPurchase` y `Gift`
  (registros financieros) conserven su FK. **Si agregás una entidad con FK a `User`, sumala a
  `AccountErasureRepository`** (salvo que sea un registro financiero); `AccountErasureRepositoryTest`
  valida el JPQL contra H2. Además, **toda lectura de perfil debe filtrar `enabled = true`** (más la
  visibilidad pública/amigos) para no exponer cuentas dadas de baja.
- **Comentarios sobrantes:** es el hallazgo #1 en los reviews. No dejes comentarios autoexplicativos ni
  “colados” del scaffold; borralos antes de pushear (ver **Comentarios**).
- **Migración faltante:** con `ddl-auto: validate`, una entidad/columna nueva sin su migración Flyway
  hace fallar el arranque. Si tocás entidades, agregá el `V<n>__…sql` en el mismo PR (ver **Versionado de esquema**).
- **Antes de pushear:** `./gradlew spotlessApply`. Es fácil olvidarlo y que CI falle sólo por formato.

## 4. Seguridad (invariantes)

- **JWT stateless.** `SessionCreationPolicy.STATELESS`, sin sesión de servidor. El access token
  se valida en `JwtAuthFilter` (se agrega antes de `UsernamePasswordAuthenticationFilter`).
- **Passwords** hasheadas con **BCrypt** (`PasswordEncoder`). Nunca almacenar ni loguear
  passwords en claro; el campo persistido es `passwordHash`.
- **Autorización por rol:** `POST /signs` requiere rol `ADMIN`. `/organizations/**` requiere `ADMIN` u `ORG_ADMIN`
  (crear orgs, `PATCH` de una org, asignar/quitar admins, invitar admins y contratar cursos: solo `ADMIN`; el back-office de orgs además vive en `/admin/**`, solo `ADMIN`);
  `POST /organizations/invite-codes/redeem` y `GET`/`DELETE /organizations/me` solo autenticación. Un `ORG_ADMIN` queda acotado a su organización en el service. Reglas en `SecurityConfig`.
- **CORS:** `CorsConfig` habilita solo los orígenes de `app.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`, separados por coma; el panel web). Vacío = sin headers CORS, la API queda cerrada a navegadores. Sin credenciales: la auth es bearer en header. `PANEL_URL` es la URL base del panel para los links de los emails.
- **Rutas públicas:** `/auth/**`, `/users/username-availability`, `POST /waitlist`, `POST /organizations/register`,
  `GET /organizations/pricing`, `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `GET /signs/{meaning}/animation`. El resto requiere autenticación.
- **401 vs 403:** token ausente, inválido o vencido → **401** (`HttpStatusEntryPoint` en `SecurityConfig`; los clientes
  refrescan el access token ante un 401). Token válido sin el rol requerido → 403 (`AccessDeniedHandler` por defecto).
- **Perfil `local`** desactiva la seguridad (`app.security.enabled=false`, cadena que permite todo)
  para desarrollo. **Nunca** activar ese comportamiento en prod.
- **Rate limiting** con Bucket4j (`RateLimitInterceptor`, por IP, registrado en `WebConfig`) en
  endpoints sensibles a abuso: 30/min en `/users/username-availability` y `/store/gem-purchases`;
  **10 cada 15 min** (`RateLimitInterceptor.forAuthEmail`) en `/auth/forgot-password`,
  `/auth/reset-password` y `/auth/resend-verification-email` (mandan mails o validan el código de
  reset). Un endpoint nuevo que mande mails sin autenticación va en ese segundo grupo. El límite de
  `/waitlist` y del resto del tráfico público lo aplica Cloudflare, delante de la API.
- **IP del cliente:** sólo `X-Real-IP`, que nginx **pisa** siempre con la IP que resolvió
  (`nginx/templates/app.conf.template`: `real_ip_header CF-Connecting-IP` confiando únicamente en los
  rangos de Cloudflare). **Nunca** leer `X-Forwarded-For` ni `CF-Connecting-IP` en la app: los manda
  el cliente y con ellos cualquiera elige su propio bucket. Si Cloudflare publica rangos nuevos
  (<https://www.cloudflare.com/ips/>), actualizar los `set_real_ip_from`; si no, esos pedidos quedan
  agrupados por IP de Cloudflare y comparten límite.
- **Enumeración de usuarios:** `forgotPassword` / `resendVerificationEmail` responden igual exista
  o no el email (no filtrar existencia de cuentas) — **también en tiempo y en status**: todo mail
  que dependa de que la cuenta exista se manda con `@Async` en `EmailService`, para que ni la
  demora del SMTP ni un fallo de envío (500) delaten la cuenta. Mantener este comportamiento.
- **Secretos** por variables de entorno (`.env`, ver `.env.example`): `JWT_SECRET`,
  `GOOGLE_CLIENT_ID`, credenciales de DB y mail, service account de Firebase, credenciales de
  Cloudflare R2 (`R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`), service account de Google Play
  (`GOOGLE_PLAY_CREDENTIALS_JSON`, base64 del JSON, con permisos *View financial data* + *Manage
  orders* en Play Console). Nunca commitear secretos.
- **Compras in-app:** el servidor es la única fuente de verdad. Un `purchaseToken` sólo acredita
  gemas si Google lo confirma como pagado (`purchaseState = purchased`) y no fue consumido ni
  acreditado antes. `google-play.enabled=false` (perfiles `local`/`test`) reemplaza el verificador
  por uno que acepta cualquier token; con el perfil `prod` activo la app **se niega a arrancar** en ese estado. El verificador real además cruza el `productId` que devuelve Google con el que mandó el cliente (un token de un pack barato no canjea uno caro).
- **R2 (animaciones):** las credenciales viven **sólo** en el backend; el cliente nunca las recibe.
  `Sign.animationUrl` guarda el *object key* (p. ej. `lsa/hola.glb`), y `GET /signs/{meaning}/animation`
  (la seña se direcciona por su `meaning`, que es único) devuelve una **presigned URL** de descarga con
  expiración corta (`r2.presign-expiry-minutes`, 15 min por defecto). No hacer público el bucket ni
  exponer el key como URL directa.
- Tokens de un solo uso (reset/verificación/refresh) se borran tras usarse; el refresh se rota.
- **Reset de contraseña por código:** `forgot-password` manda un código de 6 dígitos (`SecureRandom`)
  que vence a los 15 min (`auth.token-expirations.password-reset`) y se guarda **hasheado con BCrypt**
  en `tokens.token` (no SHA-256 sin sal: códigos iguales de dos usuarios chocarían con el `unique`). El
  hash se calcula **antes** de buscar al usuario, para no reabrir la diferencia de tiempo. `reset-password`
  recibe `{email, code, newPassword}`; cada código incorrecto suma `tokens.attempts` y al 5.º se borra.
  Inválido, vencido, agotado o email inexistente → siempre `InvalidResetCodeException` (400 "Invalid or
  expired code"). Ese método usa `@Transactional(dontRollbackOn = InvalidResetCodeException.class)`
  para que el intento fallido no se pierda en el rollback (`PasswordResetIntegrationTest`).

Detalle: `config/SecurityConfig.java`, `config/WebConfig.java` y `JwtAuthFilter`.

## 5. Performance (invariantes)

- `FetchType.LAZY` por defecto en relaciones; cargar explícito lo que se necesita.
- Lecturas con `@Transactional(readOnly = true)`.
- Endpoints de catálogo **paginados** (`Pageable`, `@PageableDefault`). No devolver listas sin acotar.
- El envío push corre **fuera** de la transacción, en `@Async` tras `AFTER_COMMIT`
  (`NotificationPushListener`): nunca hacer I/O de red dentro de la transacción de DB.
- Tokens FCM inválidos se purgan automáticamente tras el envío.

## 6. Calidad y checks (deben pasar antes de mergear)

Corren en CI (`.github/workflows/ci.yml`) y localmente:

| Check | Comando | Qué exige |
|---|---|---|
| Formato | `./gradlew spotlessCheck` (autofix: `spotlessApply`) | google-java-format AOSP, sin wildcards |
| Contenido | `./gradlew test --tests "*.ContentValidationCheckTest"` | YAML de cursos válido |
| Tests + cobertura | `./gradlew test jacocoTestReport jacocoTestCoverageVerification` | **≥ 90%** (excluye config/dto/entity/exceptions) |
| Bugs estáticos | `./gradlew spotbugsMain spotbugsTest` | Sin hallazgos |
| Build | `./gradlew build` | Compila y empaqueta |

**Todo código nuevo con lógica debe venir con tests** (la barra de cobertura del 90% es dura).

**Nunca dejes pasar un error, warning o check fallando**, aunque sea anterior a tu cambio: o lo arreglás en el mismo PR o lo anotás como deuda pendiente en la documentación, y lo aclarás en tu reporte.

Notas:
- La cobertura excluye `config`, `dto`, `entity`, `exceptions` y `*Application*` (ver `build.gradle`).
- **SpotBugs** usa `config/spotbugs-exclude.xml` como filtro. Para silenciar un hallazgo puntual y
  justificado, agregá la regla ahí o anotá con `@SuppressFBWarnings` explicando el motivo; **nunca**
  bajes el umbral global ni pongas `ignoreFailures = true`.

## 7. Comandos útiles

```bash
docker compose up --build      # Levantar app + Postgres + importar contenido
./gradlew build                # Build completo con checks
./gradlew test                 # Tests + cobertura
./gradlew spotlessApply        # Autoformatear
mkdocs serve                   # Servir la doc humana (requiere requirements-docs.txt)
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 8. Git / ramas y release

GitFlow. Ramas de trabajo `feature/*`, `fix/*`, `chore/*`, `docs/*` (en general `tipo/nombre`).

**Commits:** libertad de estilo en los commits *dentro* de la rama de desarrollo. Lo que **sí**
importa es el mensaje del **squash merge** a `develop`: debe cumplir las **Angular Commit
Conventions** porque alimenta el versionado automático (SemVer). Formato `tipo(scope): descripción`,
con tipos `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `build`, `ci`, `perf`, `style`
(`feat` → *minor*, `fix` → *patch*, un footer `BREAKING CHANGE:` → *major*).

**Integración:** a `develop` siempre con **squash merge**. A `master` solo desde `release/*` o `hotfix/*`,
y siempre con **merge commit** (nunca squash ni rebase): un squash deja un único commit no convencional
en `master` (semantic-release no publica nada) y desconecta las historias, así que la release siguiente
tiene conflictos. Después de cada release, sincronizar `master` → `develop` con una PR desde
`chore/sync-master-AAAA-MM-DD`, también con merge commit.

**Gates automáticos:**
- `ci.yml` corre en **toda rama** (`push: '**'`): spotless → validación de contenido → tests + cobertura
  → spotbugs → build. Todo debe pasar.
- `branch-policy.yml` valida el naming en cada PR: hacia `develop` exige `tipo/nombre`; hacia `master`
  sólo admite `release/*` o `hotfix/*`. Un nombre inválido **falla el PR**.

**Release (automático):** al pushear a `master`, `release.yml` corre **`semantic-release`** (preset
Angular, config en `.release.config.mjs`, rama `master`): analiza los commits desde el último release, calcula la versión SemVer, crea
el **tag** y publica el **GitHub Release**. Por eso el mensaje del squash —que termina llegando a `master`
vía `release/*`— tiene que respetar la convención. **Nunca se taggea a mano.**

## 9. Mantener la documentación (guía interna para IA)

La documentación se mantiene **sincronizada con el código en el mismo PR**. Esta guía es interna
(vive acá, no en el sitio renderizable). Principio rector: **si el código y la doc no coinciden, el
código gana** — corregí la doc.

Diferencia clave entre las dos capas:

- **`CLAUDE.md`** (este archivo) es la referencia **interna y específica del código**: puede nombrar
  clases, flags y detalles concretos.
- **`/docs`** (sitio MkDocs) reúne los **Diagramas** (entidad-relación, secuencia, estados) y un
  **Inicio** conciso con enlaces de referencia (Swagger y notas de release en GitHub). Es
  **conceptual y agnóstico al naming del código**: no metas detalle de implementación ni nombres de
  clases ahí.

### Cuándo actualizar qué

| Cambiaste… | Actualizá |
|---|---|
| Arquitectura, capas o convención global | `CLAUDE.md` §2–§3 (el sitio ya no documenta esto) |
| Reglas de seguridad (rutas públicas, roles, rate limit, tokens) | `CLAUDE.md` §4 · secuencia de auth en `docs/diagrams/sequence.md` si cambia el flujo |
| Prácticas de performance | `CLAUDE.md` §5 |
| Un dominio (responsabilidad, alcance) | `CLAUDE.md` §2 |
| Un endpoint (alta/baja/contrato) | La referencia OpenAPI es automática; actualizá el flujo en `docs/diagrams/sequence.md` si es nuevo |
| Una entidad, campo o relación | `docs/diagrams/er.md` |
| Un enum con ciclo de vida | `docs/diagrams/state.md` |
| Comandos, build, checks | `CLAUDE.md` §6–§7 · `README.md` raíz |
| Una página nueva en el sitio | `mkdocs.yml` (`nav:`) · `docs/README.md` (mapa) |

### Fuentes de verdad a cotejar (no confiar en memoria ni en la doc)

| Tema | Dónde mirar |
|---|---|
| Endpoints | `**/controller/*Controller.java` (`@*Mapping`) |
| Seguridad | `config/SecurityConfig.java`, `config/WebConfig.java` |
| Entidades y relaciones | `**/entity/*.java` (`@Table`, `@ManyToOne`, `@OneToMany`, `@JoinColumn`) |
| Errores → HTTP | `exceptions/GlobalExceptionHandler.java` |
| Pipeline de contenido | `content/service/*`, `content/validator/**` |
| Config por entorno | `resources/application*.yml` |
| Checks de calidad | `build.gradle`, `.github/workflows/ci.yml` |

### Verificar el sitio localmente

```bash
python -m pip install -r requirements-docs.txt
mkdocs serve      # http://127.0.0.1:8000 (recarga en caliente)
mkdocs build      # genera build/docs-site/ ; falla si hay nav/enlaces rotos
```
