# Convenciones del proyecto

## Idioma

- **Código** (clases, métodos, variables, paquetes y Javadoc): inglés.
- **Valores de catálogo y estados visibles para el usuario** (`SEDE_ANDINA`, `BIBLIOTECA`, `CALEFACCION`, `CO2_ALTO`...): español, porque aparecen tal cual en la API y en el dashboard.
- **Mensajes de error, documentación y commits de explicación**: español. Los prefijos de commit siguen Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`, `ci:`).

## Capas

- `domain` y `application` no importan `org.springframework`, `jakarta` ni clases HTTP. Se puede verificar con:
  `grep -rn "springframework\|jakarta" src/main/java/edu/campusnexo/domain src/main/java/edu/campusnexo/application`
- Los rangos físicos se validan **una sola vez**, en el dominio (`MeasurementLimits`, `DomainChecks`). Los DTO web solo exigen presencia (`@NotNull`).
- Los controladores no contienen reglas: convierten el DTO en comando, llaman al caso de uso y convierten el resultado en DTO.
- Toda la composición de dependencias se hace en `infrastructure/config/ApplicationConfiguration`.

## Migraciones

Nunca se edita una migración ya aplicada; se agrega una nueva `V{n}__descripcion.sql`.

## Formato

Sangría de 4 espacios, una sentencia por línea, sin imports con comodín (salvo los `import static` de constantes) y máximo unos 120 caracteres por línea.
