-- =====================================================================
--  Control IPs · ESCUSUB – Sector TICOM
--  Base de datos en línea (Supabase / PostgreSQL)
--
--  Cómo usarlo: Supabase → SQL Editor → New query → pegar TODO este
--  archivo → Run. Se puede volver a ejecutar para actualizar: no borra datos.
--
--  Seguridad: las tablas viven en el esquema privado "cip" (no expuesto a
--  la API). La app sólo puede llamar a las funciones de "public", que
--  validan la sesión y el rol en cada operación.
-- =====================================================================

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

create schema if not exists cip;
revoke all on schema cip from public;

-- --------------------------------------------------------------- Tablas

create table if not exists cip.tecnicos (
  id              bigserial primary key,
  usuario         text not null,
  password        text not null,                -- bcrypt
  nombre_completo text not null,
  rol             text not null default 'tecnico' check (rol in ('admin', 'tecnico'))
);
create unique index if not exists tecnicos_usuario_uq on cip.tecnicos (lower(usuario));

create table if not exists cip.redes (
  id        bigserial primary key,
  nombre    text not null,
  gateway   inet not null,
  prefijo   int  not null check (prefijo between 16 and 30),
  ip_inicio inet not null,
  ip_fin    inet not null
);
create unique index if not exists redes_nombre_uq on cip.redes (lower(nombre));

create table if not exists cip.dispositivos (
  id               bigserial primary key,
  nombre_equipo    text not null,
  direccion_ip     inet not null unique,         -- IP única
  direccion_mac    macaddr not null unique,      -- MAC única (normaliza cualquier formato)
  oficina          text not null,
  correo_interno   text not null,
  tecnico_id       bigint references cip.tecnicos (id) on delete set null,
  fecha_registro   timestamptz not null default now(),
  tipo             text not null default 'Institucional',
  estado           text not null default 'pendiente' check (estado in ('pendiente', 'activo')),
  activado_por     bigint references cip.tecnicos (id) on delete set null,
  fecha_activacion timestamptz,
  red_id           bigint references cip.redes (id) on delete set null
);
create index if not exists dispositivos_tecnico_idx on cip.dispositivos (tecnico_id);
create index if not exists dispositivos_red_idx on cip.dispositivos (red_id);

create table if not exists cip.sesiones (
  token      uuid primary key default gen_random_uuid(),
  tecnico_id bigint not null references cip.tecnicos (id) on delete cascade,
  expira     timestamptz not null default now() + interval '30 days'
);

alter table cip.tecnicos     enable row level security;
alter table cip.redes        enable row level security;
alter table cip.dispositivos enable row level security;
alter table cip.sesiones     enable row level security;

-- ------------------------------------------------------- Datos iniciales

-- Usuarios de control_ips.sql (mismas contraseñas; $2y$ de PHP == $2a$).
insert into cip.tecnicos (id, usuario, password, nombre_completo, rol) values
  (3, 'admin',   '$2a$12$DRJt1xoG9XslUCLXKxJtM.iL3ka9.F/ZkcPB26DaLJzF9N/UaBn9O', 'DEC', 'admin'),
  (6, 'PIZETTI', '$2a$12$GOKdkXsMGI8o/iuWTMgg6.avPwpIxp1lcnQqmX22n4qXaNVhtYkhm', 'JUAN PIZETTI', 'tecnico'),
  (8, 'GOMEZ',   '$2a$12$HsNrvAYk28vGDGJctZyfS.3m7y2AeSxgjozRfMvt1GMHhxJOSaROu', 'JUAN DE DIOS GOMEZ', 'tecnico')
on conflict do nothing;
select setval(pg_get_serial_sequence('cip.tecnicos', 'id'), greatest((select max(id) from cip.tecnicos), 1));

insert into cip.redes (id, nombre, gateway, prefijo, ip_inicio, ip_fin) values
  (1, 'Red Principal', '172.21.88.1',  23, '172.21.88.2',  '172.21.89.254'),
  (2, 'Red DMZ',       '172.23.28.1',  26, '172.23.28.2',  '172.23.28.62'),
  (3, 'Red Wifi',      '192.168.66.1', 24, '192.168.66.2', '192.168.66.254')
on conflict do nothing;
select setval(pg_get_serial_sequence('cip.redes', 'id'), greatest((select max(id) from cip.redes), 1));

-- ------------------------------------------------- Funciones internas

-- Usuario de la sesión (error 401 si no existe o venció).
create or replace function cip.usuario(p_token uuid) returns cip.tecnicos
language plpgsql stable security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos;
begin
  select t.* into u from cip.sesiones s join cip.tecnicos t on t.id = s.tecnico_id
   where s.token = p_token and s.expira > now();
  if not found then
    raise exception 'Sesión vencida. Ingresá nuevamente.' using errcode = 'PT401';
  end if;
  return u;
end $$;

create or replace function cip.admin(p_token uuid) returns cip.tecnicos
language plpgsql stable security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.usuario(p_token);
begin
  if u.rol <> 'admin' then
    raise exception 'Sólo el administrador puede realizar esta acción.' using errcode = 'PT403';
  end if;
  return u;
end $$;

create or replace function cip.subred(r cip.redes) returns cidr
language sql immutable as $$ select network(set_masklen(r.gateway, r.prefijo)) $$;

-- true si la IP se puede asignar en la red (pertenece y no es red/broadcast/gateway).
create or replace function cip.asignable(r cip.redes, ip inet) returns boolean
language sql immutable as $$
  select family(ip) = 4 and ip << cip.subred(r)
     and host(ip) not in (host(cip.subred(r)), host(broadcast(cip.subred(r))), host(r.gateway))
$$;

create or replace function cip.candidatas(r cip.redes) returns setof inet
language sql stable as $$
  select c from (select (r.ip_inicio + s) as c
                   from generate_series(0::bigint, greatest(r.ip_fin - r.ip_inicio, -1)) s) x
   where cip.asignable(r, c)
$$;

create or replace function cip.proxima_ip(r cip.redes) returns inet
language sql stable as $$
  select c from cip.candidatas(r) c
   where not exists (select 1 from cip.dispositivos d where d.direccion_ip = c)
   order by c limit 1
$$;

create or replace function cip.libres(r cip.redes) returns int
language sql stable as $$
  select count(*)::int from cip.candidatas(r) c
   where not exists (select 1 from cip.dispositivos d where d.direccion_ip = c)
$$;

create or replace function cip.fecha(t timestamptz) returns text
language sql stable as $$ select to_char(t at time zone 'America/Argentina/Buenos_Aires', 'YYYY-MM-DD HH24:MI') $$;

create or replace function cip.tecnico_json(t cip.tecnicos) returns json
language sql stable as $$
  select json_build_object('id', t.id, 'usuario', t.usuario, 'nombre_completo', t.nombre_completo, 'rol', t.rol)
$$;

create or replace function cip.red_json(r cip.redes) returns json
language sql stable as $$
  select json_build_object(
    'id', r.id, 'nombre', r.nombre, 'gateway', host(r.gateway), 'prefijo', r.prefijo,
    'cidr', cip.subred(r)::text, 'mascara', host(netmask(cip.subred(r))),
    'broadcast', host(broadcast(cip.subred(r))),
    'ip_inicio', host(r.ip_inicio), 'ip_fin', host(r.ip_fin))
$$;

create or replace function cip.dispositivo_json(p_id bigint) returns json
language sql stable as $$
  select json_build_object(
    'id', d.id, 'nombre_equipo', d.nombre_equipo,
    'direccion_ip', host(d.direccion_ip), 'direccion_mac', upper(d.direccion_mac::text),
    'oficina', d.oficina, 'correo_interno', d.correo_interno,
    'tecnico_id', d.tecnico_id, 'tecnico_nombre', t.nombre_completo,
    'fecha_registro', cip.fecha(d.fecha_registro), 'tipo', d.tipo, 'estado', d.estado,
    'activado_por_nombre', a.nombre_completo, 'fecha_activacion', cip.fecha(d.fecha_activacion),
    'red_id', d.red_id, 'red_nombre', r.nombre,
    'red_cidr', case when r.id is null then null else cip.subred(r)::text end,
    'red_mascara', case when r.id is null then null else host(netmask(cip.subred(r))) end,
    'red_gateway', host(r.gateway))
  from cip.dispositivos d
  left join cip.tecnicos t on t.id = d.tecnico_id
  left join cip.tecnicos a on a.id = d.activado_por
  left join cip.redes r on r.id = d.red_id
  where d.id = p_id
$$;

-- ============================================================ API pública
-- Todas devuelven JSON. Errores: excepción con mensaje legible.

-- ---------------------------------------------------------------- Sesión

create or replace function public.login(p_usuario text, p_password text) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos; v_token uuid;
begin
  delete from cip.sesiones where expira < now();
  select * into u from cip.tecnicos where lower(usuario) = lower(trim(p_usuario));
  if not found or u.password <> crypt(p_password, u.password) then
    perform pg_sleep(0.5);  -- frena intentos por fuerza bruta
    raise exception 'Usuario o contraseña incorrectos.';
  end if;
  insert into cip.sesiones (tecnico_id) values (u.id) returning token into v_token;
  return json_build_object('token', v_token, 'usuario', cip.tecnico_json(u));
end $$;

create or replace function public.logout(p_token uuid) returns json
language sql security definer set search_path = cip, extensions, public as $$
  delete from cip.sesiones where token = p_token;
  select 'true'::json;
$$;

create or replace function public.yo(p_token uuid) returns json
language sql stable security definer set search_path = cip, extensions, public as $$
  select cip.tecnico_json(cip.usuario(p_token))
$$;

create or replace function public.cambiar_mi_password(p_token uuid, p_actual text, p_nueva text) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.usuario(p_token);
begin
  if u.password <> crypt(p_actual, u.password) then raise exception 'La contraseña actual no es correcta.'; end if;
  if length(coalesce(p_nueva, '')) < 4 then raise exception 'La nueva contraseña debe tener al menos 4 caracteres.'; end if;
  update cip.tecnicos set password = crypt(p_nueva, gen_salt('bf', 10)) where id = u.id;
  return 'true'::json;
end $$;

-- ---------------------------------------------------------------- Redes

create or replace function public.redes_listar(p_token uuid) returns json
language plpgsql stable security definer set search_path = cip, extensions, public as $$
begin
  perform cip.usuario(p_token);
  return coalesce((
    select json_agg((cip.red_json(r)::jsonb || jsonb_build_object(
             'libres', cip.libres(r), 'proxima', host(cip.proxima_ip(r)),
             'equipos', (select count(*) from cip.dispositivos d where d.red_id = r.id)))::json order by r.id)
      from cip.redes r), '[]'::json);
end $$;

create or replace function public.red_guardar(
  p_token uuid, p_id bigint, p_nombre text, p_gateway text, p_prefijo int, p_ip_inicio text, p_ip_fin text
) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare r cip.redes; n cidr; v_gw inet;
begin
  perform cip.admin(p_token);
  if coalesce(trim(p_nombre), '') = '' then raise exception 'El nombre de la red es obligatorio.'; end if;
  if p_prefijo is null or p_prefijo not between 16 and 30 then raise exception 'El prefijo debe estar entre 16 y 30.'; end if;
  begin v_gw := host(p_gateway::inet)::inet; exception when others then raise exception 'Gateway inválido.'; end;
  if family(v_gw) <> 4 then raise exception 'Gateway inválido.'; end if;
  n := network(set_masklen(v_gw, p_prefijo));
  if host(v_gw) in (host(n), host(broadcast(n))) then
    raise exception 'El gateway no puede ser la dirección de red ni el broadcast.';
  end if;
  r := row(coalesce(p_id, 0), trim(p_nombre), v_gw, p_prefijo, null, null)::cip.redes;
  begin
    r.ip_inicio := case when coalesce(trim(p_ip_inicio), '') = '' then host(n)::inet + 1 else host(p_ip_inicio::inet)::inet end;
    r.ip_fin    := case when coalesce(trim(p_ip_fin), '') = ''    then host(broadcast(n))::inet - 1 else host(p_ip_fin::inet)::inet end;
  exception when invalid_text_representation then raise exception 'IP de inicio o fin inválida.';
  end;
  if not (r.ip_inicio << n and r.ip_fin << n) then raise exception 'El rango debe pertenecer a %.', n; end if;
  if r.ip_inicio > r.ip_fin then raise exception 'La IP inicial debe ser menor o igual a la final.'; end if;

  begin
    if p_id is null then
      insert into cip.redes (nombre, gateway, prefijo, ip_inicio, ip_fin)
      values (r.nombre, r.gateway, r.prefijo, r.ip_inicio, r.ip_fin) returning * into r;
    else
      update cip.redes set nombre = r.nombre, gateway = r.gateway, prefijo = r.prefijo,
             ip_inicio = r.ip_inicio, ip_fin = r.ip_fin
       where id = p_id returning * into r;
      if not found then raise exception 'La red ya no existe.'; end if;
    end if;
  exception when unique_violation then raise exception 'Ya existe una red con ese nombre.';
  end;
  return (cip.red_json(r)::jsonb || jsonb_build_object('libres', cip.libres(r), 'proxima', host(cip.proxima_ip(r))))::json;
end $$;

create or replace function public.red_eliminar(p_token uuid, p_id bigint) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare n int;
begin
  perform cip.admin(p_token);
  select count(*) into n from cip.dispositivos where red_id = p_id;
  if n > 0 then raise exception 'La red tiene % equipo(s) cargado(s). Movelos o eliminalos primero.', n; end if;
  delete from cip.redes where id = p_id;
  return 'true'::json;
end $$;

-- IP que recibiría un equipo nuevo en la red (sólo informativo).
create or replace function public.vista_previa_ip(p_token uuid, p_red_id bigint) returns json
language plpgsql stable security definer set search_path = cip, extensions, public as $$
declare r cip.redes;
begin
  perform cip.usuario(p_token);
  select * into r from cip.redes where id = p_red_id;
  if not found then raise exception 'La red no existe.'; end if;
  return json_build_object('ip', host(cip.proxima_ip(r)), 'libres', cip.libres(r));
end $$;

-- --------------------------------------------------------- Dispositivos

create or replace function public.dispositivos_listar(
  p_token uuid, p_estado text, p_busqueda text, p_red_id bigint
) returns json
language plpgsql stable security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.usuario(p_token); q text := '%' || coalesce(trim(p_busqueda), '') || '%';
begin
  return json_build_object(
    'items', coalesce((
      select json_agg(cip.dispositivo_json(d.id)
                      order by (d.estado = 'activo'), d.fecha_registro desc, d.id desc)
        from cip.dispositivos d
       where (u.rol = 'admin' or d.tecnico_id = u.id)
         and (p_estado is null or d.estado = p_estado)
         and (p_red_id is null or d.red_id = p_red_id)
         and (d.nombre_equipo ilike q or host(d.direccion_ip) like q or upper(d.direccion_mac::text) ilike q
              or d.oficina ilike q or d.correo_interno ilike q)), '[]'::json),
    'pendientes', (select count(*) from cip.dispositivos d where d.estado = 'pendiente' and (u.rol = 'admin' or d.tecnico_id = u.id)),
    'activos',    (select count(*) from cip.dispositivos d where d.estado = 'activo'    and (u.rol = 'admin' or d.tecnico_id = u.id))
  );
end $$;

-- Alta (p_id null) o modificación. p_ip null/vacío = asignar automáticamente la primera IP libre de la red.
create or replace function public.dispositivo_guardar(
  p_token uuid, p_id bigint, p_nombre text, p_mac text, p_oficina text, p_correo text,
  p_tipo text, p_red_id bigint, p_ip text
) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare
  u cip.tecnicos := cip.usuario(p_token);
  d cip.dispositivos; r cip.redes; otro cip.dispositivos;
  v_mac macaddr; v_ip inet; v_red bigint; v_id bigint;
begin
  if p_id is not null then
    select * into d from cip.dispositivos where id = p_id for update;
    if not found then raise exception 'El equipo ya no existe.'; end if;
    if u.rol <> 'admin' and (d.tecnico_id is distinct from u.id or d.estado = 'activo') then
      raise exception 'Sólo el administrador puede modificar este equipo.' using errcode = 'PT403';
    end if;
  end if;

  if coalesce(trim(p_nombre), '') = '' then raise exception 'El nombre del equipo es obligatorio.'; end if;
  if coalesce(trim(p_oficina), '') = '' then raise exception 'La oficina es obligatoria.'; end if;
  if coalesce(trim(p_correo), '') !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'Correo inválido.'; end if;
  if regexp_replace(coalesce(p_mac, ''), '[:\-.\s]', '', 'g') !~* '^[0-9a-f]{12}$' then
    raise exception 'MAC inválida (12 dígitos hexadecimales).';
  end if;
  v_mac := regexp_replace(p_mac, '[:\-.\s]', '', 'g')::macaddr;

  select * into otro from cip.dispositivos where direccion_mac = v_mac and id is distinct from p_id;
  if found then
    raise exception 'La MAC % ya está asignada al equipo "%" (%).', upper(v_mac::text), otro.nombre_equipo, otro.oficina;
  end if;

  if p_id is not null and u.rol <> 'admin' then
    -- El técnico no puede cambiar la red ni la IP de un equipo ya cargado.
    v_ip := d.direccion_ip; v_red := d.red_id;
  else
    select * into r from cip.redes where id = p_red_id;
    if not found then raise exception 'Elegí una red válida.'; end if;
    v_red := r.id;
    if coalesce(trim(p_ip), '') = '' then
      -- Bloqueo para que dos celulares no reciban la misma IP al mismo tiempo.
      perform pg_advisory_xact_lock(hashtext('cip.asignar_ip'));
      v_ip := cip.proxima_ip(r);
      if v_ip is null then
        raise exception 'No quedan IPs libres en % (% – %).', r.nombre, host(r.ip_inicio), host(r.ip_fin);
      end if;
    else
      begin v_ip := host(trim(p_ip)::inet)::inet;
      exception when others then raise exception 'IP inválida.';
      end;
      if not (family(v_ip) = 4 and v_ip << cip.subred(r)) then
        raise exception 'La IP % no pertenece a % (%).', host(v_ip), r.nombre, cip.subred(r);
      end if;
      if not cip.asignable(r, v_ip) then
        raise exception 'No se puede usar la dirección de red, el broadcast ni el gateway.';
      end if;
    end if;
  end if;

  select * into otro from cip.dispositivos where direccion_ip = v_ip and id is distinct from p_id;
  if found then
    raise exception 'La IP % ya está asignada al equipo "%" (%).', host(v_ip), otro.nombre_equipo, otro.oficina;
  end if;

  begin
    if p_id is null then
      insert into cip.dispositivos (nombre_equipo, direccion_ip, direccion_mac, oficina, correo_interno, tecnico_id, tipo, red_id)
      values (trim(p_nombre), v_ip, v_mac, trim(p_oficina), trim(p_correo), u.id, coalesce(nullif(trim(p_tipo), ''), 'Institucional'), v_red)
      returning id into v_id;
    else
      update cip.dispositivos set nombre_equipo = trim(p_nombre), direccion_ip = v_ip, direccion_mac = v_mac,
             oficina = trim(p_oficina), correo_interno = trim(p_correo),
             tipo = coalesce(nullif(trim(p_tipo), ''), 'Institucional'), red_id = v_red
       where id = p_id returning id into v_id;
    end if;
  exception when unique_violation then
    raise exception 'La IP o la MAC ya están registradas en otro equipo.';
  end;
  return cip.dispositivo_json(v_id);
end $$;

create or replace function public.dispositivo_estado(p_token uuid, p_id bigint, p_activo boolean) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.admin(p_token);
begin
  update cip.dispositivos
     set estado = case when p_activo then 'activo' else 'pendiente' end,
         activado_por = case when p_activo then u.id end,
         fecha_activacion = case when p_activo then now() end
   where id = p_id;
  if not found then raise exception 'El equipo ya no existe.'; end if;
  return cip.dispositivo_json(p_id);
end $$;

create or replace function public.dispositivo_eliminar(p_token uuid, p_id bigint) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.usuario(p_token); d cip.dispositivos;
begin
  select * into d from cip.dispositivos where id = p_id;
  if not found then return 'true'::json; end if;
  if u.rol <> 'admin' and (d.tecnico_id is distinct from u.id or d.estado = 'activo') then
    raise exception 'Sólo el administrador puede eliminar este equipo.' using errcode = 'PT403';
  end if;
  delete from cip.dispositivos where id = p_id;
  return 'true'::json;
end $$;

-- ------------------------------------------------------------- Usuarios

create or replace function public.tecnicos_listar(p_token uuid) returns json
language plpgsql stable security definer set search_path = cip, extensions, public as $$
begin
  perform cip.admin(p_token);
  return coalesce((select json_agg(cip.tecnico_json(t) order by t.rol, t.nombre_completo) from cip.tecnicos t), '[]'::json);
end $$;

create or replace function public.tecnico_crear(p_token uuid, p_usuario text, p_password text, p_nombre text, p_rol text) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare t cip.tecnicos;
begin
  perform cip.admin(p_token);
  if coalesce(trim(p_nombre), '') = '' then raise exception 'El nombre es obligatorio.'; end if;
  if coalesce(trim(p_usuario), '') = '' or trim(p_usuario) ~ '\s' then raise exception 'Usuario obligatorio y sin espacios.'; end if;
  if length(coalesce(p_password, '')) < 4 then raise exception 'La contraseña debe tener al menos 4 caracteres.'; end if;
  if p_rol not in ('admin', 'tecnico') then raise exception 'Rol inválido.'; end if;
  begin
    insert into cip.tecnicos (usuario, password, nombre_completo, rol)
    values (trim(p_usuario), crypt(p_password, gen_salt('bf', 10)), trim(p_nombre), p_rol) returning * into t;
  exception when unique_violation then raise exception 'El usuario "%" ya existe.', trim(p_usuario);
  end;
  return cip.tecnico_json(t);
end $$;

create or replace function public.tecnico_password(p_token uuid, p_id bigint, p_password text) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
begin
  perform cip.admin(p_token);
  if length(coalesce(p_password, '')) < 4 then raise exception 'La contraseña debe tener al menos 4 caracteres.'; end if;
  update cip.tecnicos set password = crypt(p_password, gen_salt('bf', 10)) where id = p_id;
  delete from cip.sesiones where tecnico_id = p_id and token <> p_token;  -- cierra sus otras sesiones
  return 'true'::json;
end $$;

create or replace function public.tecnico_rol(p_token uuid, p_id bigint, p_rol text) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
begin
  perform cip.admin(p_token);
  if p_rol not in ('admin', 'tecnico') then raise exception 'Rol inválido.'; end if;
  if p_rol = 'tecnico' and (select rol from cip.tecnicos where id = p_id) = 'admin'
     and (select count(*) from cip.tecnicos where rol = 'admin') <= 1 then
    raise exception 'Debe quedar al menos un administrador.';
  end if;
  update cip.tecnicos set rol = p_rol where id = p_id;
  return 'true'::json;
end $$;

create or replace function public.tecnico_eliminar(p_token uuid, p_id bigint) returns json
language plpgsql security definer set search_path = cip, extensions, public as $$
declare u cip.tecnicos := cip.admin(p_token);
begin
  if p_id = u.id then raise exception 'No podés eliminar tu propio usuario.'; end if;
  if (select rol from cip.tecnicos where id = p_id) = 'admin'
     and (select count(*) from cip.tecnicos where rol = 'admin') <= 1 then
    raise exception 'Debe quedar al menos un administrador.';
  end if;
  delete from cip.tecnicos where id = p_id;
  return 'true'::json;
end $$;

-- ------------------------------------------------------------- Permisos

revoke all on all tables in schema cip from public;
revoke all on all functions in schema cip from public;

do $$
declare f text;
begin
  foreach f in array array[
    'login(text,text)', 'logout(uuid)', 'yo(uuid)', 'cambiar_mi_password(uuid,text,text)',
    'redes_listar(uuid)', 'red_guardar(uuid,bigint,text,text,integer,text,text)', 'red_eliminar(uuid,bigint)',
    'vista_previa_ip(uuid,bigint)', 'dispositivos_listar(uuid,text,text,bigint)',
    'dispositivo_guardar(uuid,bigint,text,text,text,text,text,bigint,text)',
    'dispositivo_estado(uuid,bigint,boolean)', 'dispositivo_eliminar(uuid,bigint)',
    'tecnicos_listar(uuid)', 'tecnico_crear(uuid,text,text,text,text)', 'tecnico_password(uuid,bigint,text)',
    'tecnico_rol(uuid,bigint,text)', 'tecnico_eliminar(uuid,bigint)'
  ] loop
    execute format('revoke all on function public.%s from public', f);
    if exists (select 1 from pg_roles where rolname = 'anon') then
      execute format('grant execute on function public.%s to anon, authenticated', f);
    end if;
  end loop;
end $$;

-- Recarga la API de Supabase para que vea las funciones nuevas.
notify pgrst, 'reload schema';
