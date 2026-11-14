<p align="center"><img src="docs/logo.png" width="160" alt="ESCUSUB"></p>

# Control IPs – ESCUSUB TIC (Android)

App Android **100 % local** (sin internet ni servidor) para registrar PCs con su **IP** y **MAC**.
Los datos se guardan en una base SQLite dentro del celular, con la misma estructura que `database/control_ips.sql`.

## Instalar
1. Copiá `apk/ControlIPs.apk` al celular.
2. Abrilo y aceptá "Instalar apps de origen desconocido".
3. Ingresá con los usuarios de la base original (`admin`, `PIZETTI`, `GOMEZ`) y **las mismas contraseñas** que usaban en el sistema web (los hashes bcrypt se importaron tal cual).

## Funciones
| | Administrador | Técnico |
|---|---|---|
| Cargar PC (queda en **ROJO / pendiente**) | ✔ | ✔ |
| Ver equipos | Todos | Sólo los que cargó |
| Activar equipo (pasa a **VERDE**) / desactivar | ✔ | ✘ |
| Editar / eliminar | Todos | Sólo los suyos pendientes |
| Crear usuarios, cambiar rol, restablecer contraseña, eliminar | ✔ | ✘ |
| Exportar CSV (Excel) | ✔ | ✘ |
| Cambiar su propia contraseña | ✔ | ✔ |

- **IP y MAC no se pueden repetir**: se validan antes de guardar (indicando qué equipo ya la usa) y además la base tiene índices `UNIQUE`.
- La MAC se acepta como `AA:BB:CC:DD:EE:FF`, `AA-BB-…`, `AABB.CCDD.EEFF` o `AABBCCDDEEFF` y se guarda normalizada, así no se "cuelan" duplicados escritos distinto. La IP se normaliza (`192.168.001.010` → `192.168.1.10`).
- Búsqueda por nombre, IP, MAC, oficina o correo y filtros Todos / Pendientes / Activos.
- El logo aparece en el ícono de la app, el login, la barra superior de todas las pantallas, los diálogos y como marca de agua.

## Compilar
Requiere JDK 17+ y Android SDK 35.
```
./gradlew assembleRelease     # APK en app/build/outputs/apk/release/
./gradlew testReleaseUnitTest # pruebas de validación IP/MAC y contraseñas
```
