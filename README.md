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

## Mandar mail de verificación

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
