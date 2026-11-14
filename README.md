<p align="center"><img src="docs/logo.png" width="160" alt="ESCUSUB"></p>
<p align="center"><b>Diseñado y desarrollado por ESCUSUB · Sector TICOM</b></p>

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
| IP asignada **automáticamente** según la red elegida, avisada al guardar | ✔ (o manual) | ✔ |
| Configurar redes (alta, rango a asignar, baja) | ✔ | ✘ |
| Pantalla *Acerca de* | ✔ | ✔ |
| Ver equipos | Todos | Sólo los que cargó |
| Activar equipo (pasa a **VERDE**) / desactivar | ✔ | ✘ |
| Editar / eliminar | Todos | Sólo los suyos pendientes |
| Crear usuarios, cambiar rol, restablecer contraseña, eliminar | ✔ | ✘ |
| Exportar CSV (Excel) | ✔ | ✘ |
| Cambiar su propia contraseña | ✔ | ✔ |

- **Redes**: cada PC se carga en una red y recibe la primera IP libre de esa red (se saltean dirección de red, gateway y broadcast). El formulario muestra de antemano la IP que va a recibir y, al guardar, un cartel informa **la IP asignada**, red, máscara y gateway (con botón para copiar la IP). Si se borra un equipo, su IP vuelve a quedar libre. El admin puede desactivar el interruptor y escribir una IP a mano (debe pertenecer a la red elegida). Redes iniciales:

  | Red | Subred | Máscara | Gateway | Asigna | IPs |
  |---|---|---|---|---|---|
  | Red Principal | 172.21.88.0/23 | 255.255.254.0 | 172.21.88.1 | 172.21.88.2 – 172.21.89.254 | 509 |
  | Red DMZ | 172.23.28.0/26 | 255.255.255.192 | 172.23.28.1 | 172.23.28.2 – 172.23.28.62 | 61 |
  | Red Wifi | 192.168.66.0/24 | 255.255.255.0 | 192.168.66.1 | 192.168.66.2 – 192.168.66.254 | 253 |

  Se modifican en *menú → Redes* (por ejemplo, para reservar IPs para servidores o impresoras acortando el rango a asignar).
- **IP y MAC no se pueden repetir**: se validan antes de guardar (indicando qué equipo ya la usa) y además la base tiene índices `UNIQUE`.
- La MAC se acepta como `AA:BB:CC:DD:EE:FF`, `AA-BB-…`, `AABB.CCDD.EEFF` o `AABBCCDDEEFF` y se guarda normalizada, así no se "cuelan" duplicados escritos distinto. La IP se normaliza (`192.168.001.010` → `192.168.1.10`).
- Búsqueda por nombre, IP, MAC, oficina o correo y filtros Todos / Pendientes / Activos.
- El logo aparece en el ícono de la app, el login, la barra superior de todas las pantallas, los diálogos y como marca de agua.

## Compilar
Requiere JDK 17+ y Android SDK 35.
```
./gradlew assembleRelease     # APK en app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # pruebas: validación IP/MAC, contraseñas y apertura de pantallas (Robolectric)
```
