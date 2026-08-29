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
- **Paquete raíz:** `com.signasource.signa_api`
- **Contenido:** los cursos son **YAML versionado en el repo** (`src/main/resources/content/`),
  importado a la base por un pipeline idempotente. El YAML es la fuente de verdad, no la DB.

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
| `users` | Perfil, settings, amistades (`Friendship`: request/accept/reject/block), username, daily goal, color de header |
| `learning` | Cursos, versiones, temas, lecciones, bloques, señas y reportes de señas; seguimiento de progreso (inscripciones, progreso de tema/lección, intentos de bloque); animación de seña vía presigned URL de R2 (`GET /signs/{meaning}/animation`; `meaning` es único) |
| `content` | Pipeline de importación de contenido YAML (load → validate → persist), idempotente |
| `gamification` | Stats de usuario, logros, desafíos, tienda, compras, regalos; XP diario/semanal, señas aprendidas y mecánica de vidas/racha |
| `notification` | Tokens de dispositivo, plantillas, historial y envío push vía Firebase (FCM) |
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

**Gotchas / lecciones de code review** (recurrentes en las PRs del repo — evitalas de entrada)
- **N+1:** si vas a necesitar relaciones, traelas en una sola query con
  `@EntityGraph(attributePaths = {...})` en el método del repositorio (ver `CourseVersionRepository`,
  `LessonRepository`), en vez de encadenar varias consultas.
- **`enabled` vs `verified`:** `enabled` marca **solo** si la cuenta está activa; la baja de cuenta
  (`deleteAccount`) pone `enabled = false` y Spring (`DaoAuthenticationProvider`) bloquea su login.
  El estado del correo va en `verified` (registro nace `enabled=true, verified=false`; `verifyAccount`
  lo pone `true`). Una cuenta sin verificar **sí** puede iniciar sesión.
- **Soft-delete de cuenta** (`enabled = false`): al dar de baja hay que limpiar los tokens del usuario
  (`TokenRepository`) y sus device tokens (`DeviceTokenRepository`); y **toda lectura de perfil debe
  filtrar `enabled = true`** (más la visibilidad pública/amigos) para no exponer cuentas dadas de baja.
- **Comentarios sobrantes:** es el hallazgo #1 en los reviews. No dejes comentarios autoexplicativos ni
  “colados” del scaffold; borralos antes de pushear (ver **Comentarios**).
- **Antes de pushear:** `./gradlew spotlessApply`. Es fácil olvidarlo y que CI falle sólo por formato.

## 4. Seguridad (invariantes)

- **JWT stateless.** `SessionCreationPolicy.STATELESS`, sin sesión de servidor. El access token
  se valida en `JwtAuthFilter` (se agrega antes de `UsernamePasswordAuthenticationFilter`).
- **Passwords** hasheadas con **BCrypt** (`PasswordEncoder`). Nunca almacenar ni loguear
  passwords en claro; el campo persistido es `passwordHash`.
- **Autorización por rol:** `POST /signs` requiere rol `ADMIN`. Regla en `SecurityConfig`.
- **Rutas públicas:** `/auth/**`, `/users/username-availability`, `/actuator/health`, `/actuator/info`.
  El resto requiere autenticación.
- **Perfil `local`** desactiva la seguridad (`app.security.enabled=false`, cadena que permite todo)
  para desarrollo. **Nunca** activar ese comportamiento en prod.
- **Rate limiting** con Bucket4j (`RateLimitInterceptor`) en endpoints sensibles a abuso.
- **Enumeración de usuarios:** `forgotPassword` / `resendVerificationEmail` responden igual exista
  o no el email (no filtrar existencia de cuentas). Mantener este comportamiento.
- **Secretos** por variables de entorno (`.env`, ver `.env.example`): `JWT_SECRET`,
  `GOOGLE_CLIENT_ID`, credenciales de DB y mail, service account de Firebase, credenciales de
  Cloudflare R2 (`R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`). Nunca commitear secretos.
- **R2 (animaciones):** las credenciales viven **sólo** en el backend; el cliente nunca las recibe.
  `Sign.animationUrl` guarda el *object key* (p. ej. `lsa/test.glb`), y `GET /signs/{meaning}/animation`
  (la seña se direcciona por su `meaning`, que es único) devuelve una **presigned URL** de descarga con
  expiración corta (`r2.presign-expiry-minutes`, 15 min por defecto). No hacer público el bucket ni
  exponer el key como URL directa.
- Tokens de un solo uso (reset/verificación/refresh) se borran tras usarse; el refresh se rota.

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

**Integración:** a `develop` siempre con **squash merge**. A `master` solo desde `release/*` o `hotfix/*`.

**Gates automáticos:**
- `ci.yml` corre en **toda rama** (`push: '**'`): spotless → validación de contenido → tests + cobertura
  → spotbugs → build. Todo debe pasar.
- `branch-policy.yml` valida el naming en cada PR: hacia `develop` exige `tipo/nombre`; hacia `master`
  sólo admite `release/*` o `hotfix/*`. Un nombre inválido **falla el PR**.

**Release (automático):** al pushear a `master`, `release.yml` corre **`semantic-release`** (preset
Angular, sin `.releaserc`): analiza los commits desde el último release, calcula la versión SemVer, crea
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
