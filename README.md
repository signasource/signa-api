# signa-api


## Comandos
### Levantar el proyecto
```
docker compose up --build
```

### Formateo de código
```
./gradlew spotlessApply
```

### Coverage

Ejecutar tests y generar reporte de cobertura:
```
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
```

Abrir reporte HTML:
```
start build/reports/jacoco/test/html/index.html
```

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
