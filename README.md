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
2. La primera vez se abre **⚙ Servidor**: pegá la URL y la clave pública.
3. Ingresá con tu usuario y contraseña.

> El APK ya trae la URL del proyecto (`supabase.url` en `gradle.properties`). Para que también traiga la clave
> pública, agregá `supabase.key=...` en ese archivo (o en `local.properties`) y compilá.

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
| IP asignada **automáticamente** según la red elegida, avisada al guardar | ✔ (o manual) | ✔ |
| Configurar redes (alta, rango a asignar, baja) | ✔ | ✘ |
| Pantalla *Acerca de* | ✔ | ✔ |
| Ver equipos | Todos | Sólo los que cargó |
| Activar equipo (pasa a **VERDE**) / desactivar | ✔ | ✘ |
| Editar / eliminar | Todos | Sólo los suyos pendientes |
| Crear usuarios, cambiar rol, restablecer contraseña, eliminar | ✔ | ✘ |
| Exportar CSV (Excel) | ✔ | ✘ |
| Deslizar hacia abajo para actualizar la lista | ✔ | ✔ |
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
./gradlew testDebugUnitTest   # pruebas
```
Las pruebas de punta a punta (`OnlineTest`) levantan la app con Robolectric contra un PostgreSQL local
(`postgres`/`test`, base `controlips_test`) con `supabase/schema.sql` cargado; si no hay PostgreSQL se omiten.
