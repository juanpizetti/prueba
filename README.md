<p align="center"><img src="docs/logo.png" width="160" alt="ESCUSUB"></p>
<p align="center"><b>Diseñado y desarrollado por ESCUSUB · Sector TICOM</b></p>

# Control IPs – ESCUSUB TIC (Android)

App Android para registrar PCs con su **IP** y **MAC**, con **base de datos en línea** (Supabase / PostgreSQL)
compartida por todos los celulares. Requiere conexión: la IP la asigna el servidor en el momento,
así dos técnicos nunca reciben la misma.

## Puesta en marcha (una sola vez)

### 1. Crear la base en Supabase
1. Entrá a <https://supabase.com>, creá una cuenta y un **New project** (plan gratuito).
   Anotá la contraseña de la base y elegí la región más cercana (São Paulo).
2. En el proyecto: **SQL Editor → New query**, pegá **todo** el contenido de
   [`supabase/schema.sql`](supabase/schema.sql) y tocá **Run**.
   Crea las tablas, las 3 redes y los usuarios `admin`, `PIZETTI` y `GOMEZ` con **las mismas contraseñas** del sistema anterior.
   (Se puede volver a ejecutar para actualizar: no borra datos.)
3. En **Project Settings → API** (o *Connect*) copiá:
   - **Project URL** (`https://xxxx.supabase.co`)
   - **Clave pública**: `anon public` o `publishable` (`sb_publishable_…`). **Nunca** la `service_role` / `secret`.

### 2. Instalar la app
1. Copiá `apk/ControlIPs.apk` al celular e instalalo (permitir "orígenes desconocidos").
2. Ingresá con tu usuario y contraseña.

> El APK de `apk/` ya trae la URL y la clave pública del proyecto: los técnicos sólo instalan e ingresan.
> Para compilarlo así: `supabase.url` está en `gradle.properties` y la clave va en `local.properties`
> (`supabase.key=sb_publishable_...`, no se sube al repositorio).
> Una vez configurado, la app no muestra los datos del servidor. Para cambiarlos: **tocar 7 veces seguidas el logo** de la pantalla de ingreso.

### Seguridad
- Las tablas están en el esquema privado `cip`, que **no** se expone por la API. La clave pública sólo permite
  llamar a las funciones de la app, que verifican la sesión y el rol (admin/técnico) en cada operación.
- Contraseñas guardadas con bcrypt; las sesiones vencen a los 30 días.
- IP y MAC únicas a nivel base de datos; la asignación automática usa un bloqueo para que dos celulares
  simultáneos nunca reciban la misma IP.
- Los datos se pueden ver/editar desde Supabase → **Table Editor** → esquema `cip`.

## Funciones
| | Administrador | Técnico |
|---|---|---|
| Cargar PC (queda en **ROJO / pendiente**) | ✔ | ✔ |
| IP **manual** (por defecto) o **automática** (primera libre de la red), avisada al guardar | ✔ | ✔ |
| Configurar redes (alta, rango a asignar, baja) | ✔ | ✘ |
| Pantalla *Acerca de* | ✔ | ✔ |
| Ver equipos | Todos | Sólo los que cargó |
| Activar equipo (pasa a **VERDE**) / desactivar | ✔ | ✘ |
| Editar / eliminar | Todos | Sólo los suyos pendientes |
| Crear usuarios, cambiar rol, restablecer contraseña, eliminar | ✔ | ✘ |
| Exportar CSV (Excel) | ✔ | ✘ |
| Deslizar hacia abajo para actualizar la lista | ✔ | ✔ |
| Cambiar su propia contraseña | ✔ | ✔ |

- **Redes**: cada PC se carga en una red y recibe la primera IP libre de esa red (se saltean dirección de red, gateway y broadcast). Por defecto la IP se **escribe a mano** (debe pertenecer a la red elegida y no estar usada); debajo se sugiere la primera IP libre. Con el interruptor **"Asignar automáticamente"** el servidor asigna la primera IP libre de la red. Al guardar, un cartel informa **la IP asignada**, red, máscara y gateway (con botón para copiar la IP). Si se borra un equipo, su IP vuelve a quedar libre. Redes iniciales:

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
./gradlew testDebugUnitTest   # pruebas
```
### Firma (actualizaciones)
Todas las versiones se firman con la **misma clave** para que se instalen encima de la anterior sin desinstalar.
La clave **no está en el repositorio** (quien la tenga puede publicar "actualizaciones" de la app). Hacen falta dos archivos
en la raíz del proyecto, que guarda el responsable de TICOM:
- `firma/controlips-firma.p12`
- `keystore.properties` (ruta, alias y contraseña)

Si faltan, el APK se firma con una clave de prueba y **no** se instala encima de las versiones publicadas.
Huella SHA-256 del certificado: `2E:B8:8F:05:13:D2:BE:A1:69:77:D5:E1:18:5D:8D:DF:E0:0B:0D:9E:1B:51:12:92:6A:D1:C3:2F:A1:42:21:E5`

Las pruebas de punta a punta (`OnlineTest`) levantan la app con Robolectric contra un PostgreSQL local
(`postgres`/`test`, base `controlips_test`) con `supabase/schema.sql` cargado; si no hay PostgreSQL se omiten.
