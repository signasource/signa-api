# signa-api

Backend de **SignaSource**, plataforma de aprendizaje de lenguas de señas (Spring Boot / Java 21).

## Documentación

- **[`CLAUDE.md`](CLAUDE.md)** — contexto de alto nivel del repo (arquitectura, convenciones,
  seguridad, performance). Punto de partida para agentes de IA.
- **[`docs/`](docs/README.md)** — documentación técnica detallada + sitio renderizable con diagramas
  (clases, secuencia, estados, entidad-relación).

  ```bash
  python -m pip install -r requirements-docs.txt
  mkdocs serve        # http://127.0.0.1:8000
  ```

> La documentación se mantiene sincronizada con el código en cada PR. Ver la guía de mantenimiento
> para IA en [`CLAUDE.md`](CLAUDE.md) (§9).

## Comandos
### Levantar el proyecto
```
docker compose up --build
```

### Correr la API fuera de Docker (`./gradlew bootRun`)

Con un Postgres 17 propio (alcanza `docker run -d -p 55432:5432 -e POSTGRES_DB=signa -e POSTGRES_USER=signa -e POSTGRES_PASSWORD=signa postgres:17`),
además de las variables de `.env.example` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `MAIL_*`) hacen falta:

| Variable | Valor | Por qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | Seguridad desactivada y Google Play en modo falso. |
| `SPRING_DOCKER_COMPOSE_ENABLED` | `false` | Si no, `spring-boot-docker-compose` intenta levantar todo `docker-compose.yml` (API, nginx, alloy). |
| `JAVA_TOOL_OPTIONS` | `-Duser.timezone=America/Argentina/Buenos_Aires` | La JVM de Windows usa por defecto `America/Buenos_Aires`, que Postgres 17 rechaza al conectar. |
| `FIREBASE_CREDENTIALS_JSON` | base64 de un JSON de service account | `FirebaseService` necesita el bean aunque no mandes push. Sirve uno falso generado localmente (con cualquier clave RSA): arranca y no envía nada. |

Usá una base **vacía**: una creada antes de Flyway (con el viejo `ddl-auto: update`) queda marcada
como baseline V1 sin correr esa migración, y `validate` falla al arrancar (p. ej. `missing column
[reward_gems]`). Recreala o creá otra con `createdb`.

### Checks de calidad

Los mismos 5 que corre el CI (`.github/workflows/ci.yml`). Fuente de verdad y detalle: **[`CLAUDE.md` §6](CLAUDE.md)**.

| Check | Qué previene | Comando |
|---|---|---|
| **Spotless** | Formato y wildcard imports | `./gradlew spotlessCheck` (autofix: `./gradlew spotlessApply`) |
| **Contenido** | YAML de cursos inválido | `./gradlew test --tests "*.ContentValidationCheckTest"` |
| **JaCoCo** | Código sin cobertura de tests (mínimo 90%) | `./gradlew test jacocoTestReport jacocoTestCoverageVerification` |
| **SpotBugs** | Null pointers, resource leaks, lógica incorrecta, bugs de seguridad | `./gradlew spotbugsMain spotbugsTest` |
| **Build** | Que no compile o no empaquete | `./gradlew build` |

Abrir reportes HTML:
```
start build/reports/spotbugs/main.html
start build/reports/jacoco/test/html/index.html
```

## API Documentation

Swagger UI:
http://localhost:8080/swagger-ui/index.html

OpenAPI spec:
http://localhost:8080/v3/api-docs

## Estrategia de Ramas

El proyecto sigue un flujo de trabajo basado en **GitFlow**:

> El detalle del versionado automático (`semantic-release`) y los gates de CI (`branch-policy.yml`)
> está en **[`CLAUDE.md` §8](CLAUDE.md)**. Esta sección es el resumen para humanos.

| Rama | Descripción | Notas |
|---|---|---|
| `master` | Producción | Cada push genera un tag automático siguiendo [Semantic Versioning](https://semver.org/) en base al contenido de los commits |
| `develop` | Integración | Punto de unión para nuevas funcionalidades |

**Flujo de Desarrollo**:
1. **Creación**: Se puede trabajar sobre ramas con cualquier formato `*/*` como `feature/*`, `fix/*`, `chore/*`, `docs/*`, etc.
2. **Commits**: Libertad de estilo durante el desarrollo dentro de las ramas de desarrollo.
3. **Integración**: El Squash Merge es obligatorio al integrar hacia `develop`.
    - El mensaje de commit resultante **debe** cumplir con [Angular Commit Message Conventions](https://github.com/angular/angular/blob/main/contributing-docs/commit-message-guidelines.md) para el versionado automático.

**Paso a producción**:
  - Solo se permiten merges hacia `master` provenientes de ramas `release/*` o `hotfix/*`.

## Deploy a producción

No hay deploy automático: `release.yml` solo crea el tag y el GitHub Release. El servidor se
actualiza a mano por ssh. Las fallas conocidas del entorno de producción están en
[`CLAUDE.md` §10](CLAUDE.md) (deuda técnica).

- **Servidor:** droplet de DigitalOcean (Ubuntu 24.04), repo clonado en `/opt/signa-api` (dueño
  `root`). Hoy sigue la rama `develop` (ver D5 en `CLAUDE.md` §10).
- **Acceso:** `ssh <usuario>@<ip>` y `sudo -i`. Docker requiere root (o el grupo `docker`).
- **Secretos:** `/opt/signa-api/.env` (no versionado). Ojo: el `docker-compose.yml` pasa al contenedor
  **solo** las variables listadas en `environment:`; una variable nueva va en los dos lugares.

### Pasos

```bash
cd /opt/signa-api
git status && git log -1 --oneline      # rama actual y versión desplegada
git fetch origin
git log --oneline HEAD..origin/develop  # qué se va a desplegar
git diff --stat HEAD origin/develop -- nginx docker-compose.yml .env.example src/main/resources/db/migration
git pull --ff-only
```

Según lo que muestre el `diff --stat`:

1. **Cambió `nginx/`:** reiniciar nginx **antes** que la API (`docker compose restart nginx`). El
   template está montado como volumen y nginx solo lo procesa al arrancar. Si la API nueva depende de
   un header que pone el nginx nuevo, al revés queda una ventana rota.
2. **Cambió `.env.example` o el `environment:` del compose:** completar `.env` antes de seguir.
3. **Hay migraciones nuevas:** Flyway las aplica solas al arrancar. No hace falta nada.

```bash
docker compose up -d --build            # reconstruye y reinicia la API
docker compose logs -f signa-api        # Ctrl+C sale de los logs (la API sigue corriendo)
```

En los logs, confirmar `Successfully applied N migration(s)` (si había), `Content import (STARTUP)
succeeded` y `Started SignaApiApplication`. Un `FAILED` del import no tira la API (queda registrado y
se ve en el panel); una migración fallida sí.

**Nunca `docker compose down -v`:** el `-v` borra el volumen de Postgres, es decir, la base de
producción.

### Consultas puntuales a la base

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## Mandar mail de verificación

> **Producción hoy no envía mails** (puertos SMTP bloqueados por el proveedor del servidor). Ver D1
> en [`CLAUDE.md` §10](CLAUDE.md).

```
MAIL_PASSWORD= // Contraseña que se genera en http://myaccount.google.com/apppasswords
MAIL_USERNAME= // Mail
```

## Configuración de Google Auth

Para que funcione el inicio de sesión con Google, es necesario configurar la variable `GOOGLE_CLIENT_ID` en el archivo `.env`.

**¿Cómo obtener el Client ID?**
1. Ingresar a la [Consola de Google Cloud](https://console.cloud.google.com/).
2. Crear un proyecto nuevo o seleccionar uno existente.
3. Ir al menú lateral > **API y Servicios** > **Credenciales**.
4. Clic en **Crear credenciales** > **ID de cliente de OAuth**.
5. Seleccionar como Tipo de aplicación: **Aplicación web**.
6. Configurar los orígenes autorizados (ej. `http://localhost:8080` o el puerto del frontend).
7. Copiar el **ID de cliente** generado y pegarlo en el `.env`.
