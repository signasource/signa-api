# signa-api


## Comandos
### Levantar el proyecto
```
docker compose up --build
```

### Checks de calidad

| Check | Qué previene | Comando |
|---|---|---|
| **Spotless** | Formato y estándares | `./gradlew spotlessCheck` / `./gradlew spotlessApply` |
| **SpotBugs** | Null pointers, resource leaks, lógica incorrecta, bugs de seguridad | `./gradlew spotbugsMain` |
| **JaCoCo** | Código sin cobertura de tests (mínimo 90%) | `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification` |

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

El proyecto sige un flujo de trabajo basado en **GitFlow**:


| Rama | Descripcion | Notas |
|---|---|---|
| `master` | Producción | Cada push genera un tag automático siguiendo [Semantic Versioning](https://semver.org/) en base al contenido de los commits |
| `develop` | Integración | Punto de unión para nuevas funcionalidades |

**Flujo de Desarrollo**:
1. **Creación**: Se puede trabajar sobre ramas con cualquier formato `*/*` como `feature/*`, `fix/*`, `chore/*`, `docs/*`, etc.
2. **Commits**: Libertad de estilo durante el desarrollo dentro de las ramas de desarrollo.
3. **Integración**: El Squash Merge es obligatorio al integrar hacia `develop`.
    - El mensaje de commit resultante **debe** cumplir con [Angular Commit Message Conventions](https://github.com/angular/angular/blob/main/contributing-docs/commit-message-guidelines.md) para el vesionado automático.

**Paso a producción**:
  - Solo se permiten merges hacia `master` provenientes de ramas `release/*` o `hotfix/*`.

## Mandar mail de verificación

```
MAIL_PASSWORD= // Contraseña que se genera en http://myaccount.google.com/apppasswords
MAIL_USERNAME= // Mail
```

## Test

Hola
