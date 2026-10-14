-- =====================================================================
-- V1 — Esquema final del inventario de ferretería (PostgreSQL 16+)
-- Flyway lo ejecuta dentro del esquema «ferreteria» (spring.flyway.default-schema).
-- Documentación: docs/04-base-de-datos.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Tipos enumerados
-- ---------------------------------------------------------------------
CREATE TYPE estado_inventario AS ENUM ('DISPONIBLE', 'RESERVADA', 'DANADA');

-- Confirmados: ENTRADA_COMPRA, SALIDA_VENTA.
-- [ADOPTADO] de soporte: CAMBIO_ESTADO (no altera el total), SALDO_INICIAL (migración),
-- ANULACION_COMPRA y ANULACION_VENTA (reverso exacto de un movimiento confirmado).
CREATE TYPE tipo_movimiento AS ENUM (
  'ENTRADA_COMPRA', 'SALIDA_VENTA', 'CAMBIO_ESTADO', 'SALDO_INICIAL',
  'ANULACION_COMPRA', 'ANULACION_VENTA');

CREATE TYPE origen_operacion AS ENUM ('EN_LINEA', 'FUERA_DE_LINEA', 'MIGRACION', 'INTEGRACION');

-- ---------------------------------------------------------------------
-- 1. Seguridad: usuarios, roles, permisos y dispositivos
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid            uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  login           varchar(60)  NOT NULL UNIQUE,
  nombre_completo varchar(150) NOT NULL,
  correo          varchar(150),
  hash_contrasena text         NOT NULL,
  activo          boolean      NOT NULL DEFAULT true,
  ultimo_acceso   timestamptz,
  creado_en       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE rol (
  id          smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo      varchar(40)  NOT NULL UNIQUE,
  nombre      varchar(100) NOT NULL,
  descripcion text
);

CREATE TABLE permiso (
  id          smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo      varchar(60)  NOT NULL UNIQUE,   -- p. ej. VENTA_EMITIR, PRECIO_CAMBIAR, CIERRE_REABRIR
  descripcion varchar(200) NOT NULL
);

CREATE TABLE rol_permiso (
  rol_id     smallint NOT NULL REFERENCES rol(id) ON DELETE CASCADE,
  permiso_id smallint NOT NULL REFERENCES permiso(id),
  PRIMARY KEY (rol_id, permiso_id)
);

CREATE TABLE usuario_rol (
  usuario_id bigint   NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  rol_id     smallint NOT NULL REFERENCES rol(id),
  PRIMARY KEY (usuario_id, rol_id)
);

-- Equipo (caja, computador o terminal) autorizado; necesario para operar sin internet.
CREATE TABLE dispositivo (
  id                    smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid                  uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  nombre                varchar(80) NOT NULL UNIQUE,
  permite_fuera_linea   boolean NOT NULL DEFAULT false,
  activo                boolean NOT NULL DEFAULT true,
  ultima_sincronizacion timestamptz
);

-- ---------------------------------------------------------------------
-- 2. Catálogo: unidades, categorías, marcas, productos y referencias
-- ---------------------------------------------------------------------
CREATE TABLE unidad_medida (
  id        smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo    varchar(10) NOT NULL UNIQUE,   -- UND, M, KG, LB, GL, CAJA, ROLLO, BULTO...
  nombre    varchar(40) NOT NULL,
  magnitud  varchar(15) NOT NULL CHECK (magnitud IN ('CONTEO','LONGITUD','MASA','VOLUMEN','EMPAQUE'))
);

CREATE TABLE categoria (
  id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre   varchar(80) NOT NULL,
  padre_id integer REFERENCES categoria(id),
  UNIQUE (padre_id, nombre)
);

CREATE TABLE marca (
  id     integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre varchar(80) NOT NULL UNIQUE,
  activo boolean NOT NULL DEFAULT true
);

-- [ADOPTADO] tarifas de IVA colombianas; régimen tributario por confirmar.
CREATE TABLE tarifa_impuesto (
  id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo     varchar(10)  NOT NULL UNIQUE,  -- IVA19, IVA5, EXCLUIDO
  porcentaje numeric(5,2) NOT NULL CHECK (porcentaje >= 0)
);

-- Producto genérico: lo que el cliente pide («pintura vinilo blanca»).
CREATE TABLE producto (
  id                   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo_interno       varchar(30)  NOT NULL UNIQUE,
  nombre               varchar(150) NOT NULL,
  descripcion          text,
  categoria_id         integer NOT NULL REFERENCES categoria(id),
  controla_vencimiento boolean NOT NULL DEFAULT false,   -- obliga a registrar lote y fecha de vencimiento
  activo               boolean NOT NULL DEFAULT true,
  creado_en            timestamptz NOT NULL DEFAULT now()
);

-- Referencia vendible (SKU) = producto + marca + presentación. Tiene UN solo código de barras.
CREATE TABLE referencia (
  id                    integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid                  uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  producto_id           integer NOT NULL REFERENCES producto(id),
  marca_id              integer NOT NULL REFERENCES marca(id),
  presentacion          varchar(80) NOT NULL,                 -- «Galón 3,785 L», «Rollo 100 m»
  codigo_barras         varchar(14) NOT NULL UNIQUE,           -- un solo código, único global
  unidad_base_id        smallint NOT NULL REFERENCES unidad_medida(id), -- unidad de control de existencias
  permite_fraccion      boolean NOT NULL DEFAULT false,
  fraccion_minima       numeric(18,4) NOT NULL DEFAULT 1 CHECK (fraccion_minima > 0),
  meses_garantia        smallint CHECK (meses_garantia > 0),   -- NULL = sin garantía
  tarifa_impuesto_id    smallint NOT NULL REFERENCES tarifa_impuesto(id),
  porcentaje_incremento numeric(6,2) NOT NULL DEFAULT 0 CHECK (porcentaje_incremento >= 0),
  multiplo_redondeo     numeric(10,2) NOT NULL DEFAULT 50 CHECK (multiplo_redondeo > 0), -- [ADOPTADO]
  ultimo_costo          numeric(18,4) NOT NULL DEFAULT 0 CHECK (ultimo_costo >= 0),    -- por unidad base, sin IVA
  costo_promedio        numeric(18,4) NOT NULL DEFAULT 0 CHECK (costo_promedio >= 0),  -- por unidad base, sin IVA
  precio_venta          numeric(18,2) NOT NULL DEFAULT 0 CHECK (precio_venta >= 0),    -- por unidad base, sin IVA
  stock_minimo          numeric(18,4) CHECK (stock_minimo >= 0),
  activo                boolean NOT NULL DEFAULT true,
  creado_en             timestamptz NOT NULL DEFAULT now(),
  UNIQUE (producto_id, marca_id, presentacion),
  CHECK (permite_fraccion OR fraccion_minima = 1)
);
CREATE INDEX ix_referencia_producto ON referencia (producto_id);
CREATE INDEX ix_referencia_marca    ON referencia (marca_id);

-- Unidades en que se compra o vende cada referencia y su factor hacia la unidad base.
-- Ej.: cable (base M): ROLLO = 100; tornillo (base UND): CAJA = 100; puntilla (base KG): LB = 0,5.
CREATE TABLE referencia_unidad (
  referencia_id  integer  NOT NULL REFERENCES referencia(id) ON DELETE CASCADE,
  unidad_id      smallint NOT NULL REFERENCES unidad_medida(id),
  factor_a_base  numeric(18,6) NOT NULL CHECK (factor_a_base > 0),
  uso_compra     boolean NOT NULL DEFAULT true,
  uso_venta      boolean NOT NULL DEFAULT true,
  PRIMARY KEY (referencia_id, unidad_id)
);

-- Lote: obligatorio cuando el producto controla vencimiento.
CREATE TABLE lote (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  referencia_id     integer NOT NULL REFERENCES referencia(id),
  codigo_lote       varchar(40) NOT NULL,
  fecha_fabricacion date,
  fecha_vencimiento date NOT NULL,
  creado_en         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (referencia_id, codigo_lote),
  CHECK (fecha_fabricacion IS NULL OR fecha_fabricacion <= fecha_vencimiento)
);
CREATE INDEX ix_lote_vencimiento ON lote (fecha_vencimiento);

-- ---------------------------------------------------------------------
-- 3. Terceros (clientes y proveedores)
-- ---------------------------------------------------------------------
CREATE TABLE tercero (
  id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid                  uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  tipo_persona          varchar(8)  NOT NULL CHECK (tipo_persona IN ('NATURAL','JURIDICA')),
  tipo_identificacion   varchar(4)  NOT NULL CHECK (tipo_identificacion IN ('CC','CE','NIT','PAS')),
  numero_identificacion varchar(20) NOT NULL CHECK (numero_identificacion ~ '^[0-9A-Za-z]+$'),
  digito_verificacion   smallint CHECK (digito_verificacion BETWEEN 0 AND 9),
  nombres               varchar(100),
  apellidos             varchar(100),
  razon_social          varchar(200),
  direccion             varchar(200),
  municipio             varchar(80),
  telefono              varchar(20),
  correo                varchar(150) CHECK (correo IS NULL OR correo ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  es_cliente            boolean NOT NULL DEFAULT false,
  es_proveedor          boolean NOT NULL DEFAULT false,
  activo                boolean NOT NULL DEFAULT true,
  creado_en             timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tipo_identificacion, numero_identificacion),
  CONSTRAINT ck_tercero_rol CHECK (es_cliente OR es_proveedor),
  CONSTRAINT ck_tercero_natural CHECK (tipo_persona <> 'NATURAL'  OR (nombres IS NOT NULL AND apellidos IS NOT NULL)),
  CONSTRAINT ck_tercero_juridica CHECK (tipo_persona <> 'JURIDICA' OR (razon_social IS NOT NULL AND tipo_identificacion = 'NIT')),
  CONSTRAINT ck_tercero_nit_dv CHECK (tipo_identificacion <> 'NIT' OR digito_verificacion IS NOT NULL)
);
CREATE INDEX ix_tercero_nombre ON tercero (lower(coalesce(razon_social, nombres || ' ' || apellidos)));

-- ---------------------------------------------------------------------
-- 4. Numeración de documentos (rangos por dispositivo para operar sin internet)
-- ---------------------------------------------------------------------
CREATE TABLE consecutivo (
  id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tipo_documento varchar(10) NOT NULL CHECK (tipo_documento IN ('INGRESO','FACTURA')),
  prefijo        varchar(10) NOT NULL,
  dispositivo_id smallint REFERENCES dispositivo(id),  -- NULL = rango del servidor
  numero_desde   bigint NOT NULL CHECK (numero_desde > 0),
  numero_hasta   bigint NOT NULL,
  numero_actual  bigint NOT NULL,
  resolucion     varchar(60),                          -- resolución DIAN para facturas [POR DEFINIR]
  vigente_hasta  date,
  activo         boolean NOT NULL DEFAULT true,
  CHECK (numero_hasta >= numero_desde),
  CHECK (numero_actual BETWEEN numero_desde - 1 AND numero_hasta),
  UNIQUE (tipo_documento, prefijo, numero_desde)
);

-- ---------------------------------------------------------------------
-- 5. Períodos y cierres mensuales
-- ---------------------------------------------------------------------
CREATE TABLE periodo (
  id                integer PRIMARY KEY CHECK (id BETWEEN 200001 AND 299912 AND id % 100 BETWEEN 1 AND 12), -- AAAAMM
  fecha_inicio      date NOT NULL,
  fecha_fin         date NOT NULL,
  estado            varchar(10) NOT NULL DEFAULT 'ABIERTO'
                    CHECK (estado IN ('ABIERTO','EN_CIERRE','CERRADO','REABIERTO')),
  cierre_programado timestamptz NOT NULL,   -- [ADOPTADO] día 1 del mes siguiente, 02:00
  cerrado_en        timestamptz,
  cerrado_por       bigint REFERENCES usuario(id),
  reabierto_en      timestamptz,
  reabierto_por     bigint REFERENCES usuario(id),
  motivo_reapertura text,
  CHECK (fecha_fin >= fecha_inicio),
  CHECK (estado <> 'CERRADO' OR cerrado_en IS NOT NULL),
  CHECK (estado <> 'REABIERTO' OR (reabierto_por IS NOT NULL AND motivo_reapertura IS NOT NULL))
);

-- Fotografía de saldos al cierre (base del kárdex y de la valoración del período).
CREATE TABLE cierre_saldo (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  periodo_id     integer NOT NULL REFERENCES periodo(id),
  referencia_id  integer NOT NULL REFERENCES referencia(id),
  lote_id        bigint  REFERENCES lote(id),
  estado         estado_inventario NOT NULL,
  cantidad       numeric(18,4) NOT NULL CHECK (cantidad >= 0),
  costo_promedio numeric(18,4) NOT NULL,
  valor_total    numeric(18,2) NOT NULL,
  UNIQUE NULLS NOT DISTINCT (periodo_id, referencia_id, lote_id, estado)
);

-- ---------------------------------------------------------------------
-- 6. Compras: documento de ingreso
-- ---------------------------------------------------------------------
CREATE TABLE compra (
  id                        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid                      uuid NOT NULL UNIQUE,                 -- generado por el cliente (idempotencia)
  prefijo                   varchar(10) NOT NULL,
  numero_ingreso            bigint NOT NULL,
  proveedor_id              bigint NOT NULL REFERENCES tercero(id),
  numero_factura_proveedor  varchar(40) NOT NULL,
  fecha_factura_proveedor   date NOT NULL,
  fecha_efectiva            timestamptz NOT NULL DEFAULT now(),
  estado                    varchar(10) NOT NULL DEFAULT 'BORRADOR'
                            CHECK (estado IN ('BORRADOR','CONFIRMADA','ANULADA')),
  subtotal                  numeric(18,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  valor_impuestos           numeric(18,2) NOT NULL DEFAULT 0 CHECK (valor_impuestos >= 0),
  total                     numeric(18,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  observaciones             text,
  usuario_id                bigint   NOT NULL REFERENCES usuario(id),
  dispositivo_id            smallint REFERENCES dispositivo(id),
  origen                    origen_operacion NOT NULL DEFAULT 'EN_LINEA',
  creado_en                 timestamptz NOT NULL DEFAULT now(),
  confirmado_en             timestamptz,
  UNIQUE (prefijo, numero_ingreso),
  UNIQUE (proveedor_id, numero_factura_proveedor)
);
CREATE INDEX ix_compra_fecha ON compra (fecha_efectiva);

CREATE TABLE compra_detalle (
  id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  compra_id           bigint   NOT NULL REFERENCES compra(id) ON DELETE CASCADE,
  linea               smallint NOT NULL,
  referencia_id       integer  NOT NULL REFERENCES referencia(id),
  unidad_id           smallint NOT NULL,
  cantidad            numeric(18,4) NOT NULL CHECK (cantidad > 0),          -- en la unidad comprada
  factor_a_base       numeric(18,6) NOT NULL CHECK (factor_a_base > 0),
  cantidad_base       numeric(18,4) GENERATED ALWAYS AS (round(cantidad * factor_a_base, 4)) STORED,
  costo_unitario      numeric(18,4) NOT NULL CHECK (costo_unitario >= 0),   -- por unidad comprada, sin IVA
  porcentaje_impuesto numeric(5,2)  NOT NULL,
  lote_id             bigint REFERENCES lote(id),
  total_linea         numeric(18,2) GENERATED ALWAYS AS (round(cantidad * costo_unitario, 2)) STORED,
  UNIQUE (compra_id, linea),
  FOREIGN KEY (referencia_id, unidad_id) REFERENCES referencia_unidad(referencia_id, unidad_id)
);
CREATE INDEX ix_compra_detalle_ref ON compra_detalle (referencia_id);

-- ---------------------------------------------------------------------
-- 7. Ventas: factura
-- ---------------------------------------------------------------------
CREATE TABLE venta (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid              uuid NOT NULL UNIQUE,
  prefijo           varchar(10) NOT NULL,
  numero_factura    bigint NOT NULL,
  cliente_id        bigint NOT NULL REFERENCES tercero(id),
  fecha_emision     timestamptz NOT NULL DEFAULT now(),
  forma_pago        varchar(8) NOT NULL DEFAULT 'CONTADO' CHECK (forma_pago IN ('CONTADO','CREDITO')),
  estado            varchar(10) NOT NULL DEFAULT 'BORRADOR'
                    CHECK (estado IN ('BORRADOR','EMITIDA','ANULADA')),
  subtotal          numeric(18,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  valor_descuento   numeric(18,2) NOT NULL DEFAULT 0 CHECK (valor_descuento >= 0),
  valor_impuestos   numeric(18,2) NOT NULL DEFAULT 0 CHECK (valor_impuestos >= 0),
  total             numeric(18,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  cufe              varchar(96) UNIQUE,                 -- factura electrónica [POR DEFINIR]
  estado_envio_fe   varchar(12) CHECK (estado_envio_fe IN ('PENDIENTE','ACEPTADA','RECHAZADA')),
  usuario_id        bigint   NOT NULL REFERENCES usuario(id),
  dispositivo_id    smallint REFERENCES dispositivo(id),
  origen            origen_operacion NOT NULL DEFAULT 'EN_LINEA',
  creado_en         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (prefijo, numero_factura)
);
CREATE INDEX ix_venta_fecha   ON venta (fecha_emision);
CREATE INDEX ix_venta_cliente ON venta (cliente_id);

CREATE TABLE venta_detalle (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  venta_id             bigint   NOT NULL REFERENCES venta(id) ON DELETE CASCADE,
  linea                smallint NOT NULL,
  referencia_id        integer  NOT NULL REFERENCES referencia(id),
  unidad_id            smallint NOT NULL,
  lote_id              bigint REFERENCES lote(id),
  cantidad             numeric(18,4) NOT NULL CHECK (cantidad > 0),
  factor_a_base        numeric(18,6) NOT NULL CHECK (factor_a_base > 0),
  cantidad_base        numeric(18,4) GENERATED ALWAYS AS (round(cantidad * factor_a_base, 4)) STORED,
  precio_unitario      numeric(18,2) NOT NULL CHECK (precio_unitario >= 0),  -- por unidad vendida, sin IVA
  valor_descuento      numeric(18,2) NOT NULL DEFAULT 0 CHECK (valor_descuento >= 0),
  porcentaje_impuesto  numeric(5,2)  NOT NULL,
  costo_unitario_base  numeric(18,4) NOT NULL DEFAULT 0,   -- costo promedio al vender (margen)
  fecha_fin_garantia   date,                               -- calculada si la referencia tiene garantía
  total_linea          numeric(18,2) GENERATED ALWAYS AS (round(cantidad * precio_unitario, 2) - valor_descuento) STORED,
  UNIQUE (venta_id, linea),
  FOREIGN KEY (referencia_id, unidad_id) REFERENCES referencia_unidad(referencia_id, unidad_id)
);
CREATE INDEX ix_venta_detalle_ref ON venta_detalle (referencia_id);

-- Reserva: aparta existencias disponibles para un cliente (DISPONIBLE -> RESERVADA).
CREATE TABLE reserva (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  uuid          uuid NOT NULL UNIQUE,
  cliente_id    bigint  NOT NULL REFERENCES tercero(id),
  referencia_id integer NOT NULL REFERENCES referencia(id),
  lote_id       bigint REFERENCES lote(id),
  cantidad_base numeric(18,4) NOT NULL CHECK (cantidad_base > 0),
  estado        varchar(10) NOT NULL DEFAULT 'ACTIVA'
                CHECK (estado IN ('ACTIVA','CONSUMIDA','LIBERADA','VENCIDA')),
  vence_en      timestamptz NOT NULL,          -- [ADOPTADO] 72 horas por defecto
  venta_id      bigint REFERENCES venta(id),
  usuario_id    bigint NOT NULL REFERENCES usuario(id),
  creado_en     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reserva_activa ON reserva (vence_en) WHERE estado = 'ACTIVA';

-- Soporte de los cambios de estado que no son reservas (dañado / recuperado).
CREATE TABLE cambio_estado (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  referencia_id  integer NOT NULL REFERENCES referencia(id),
  lote_id        bigint REFERENCES lote(id),
  estado_origen  estado_inventario NOT NULL,
  estado_destino estado_inventario NOT NULL,
  cantidad_base  numeric(18,4) NOT NULL CHECK (cantidad_base > 0),
  motivo         varchar(300) NOT NULL,
  usuario_id     bigint NOT NULL REFERENCES usuario(id),
  creado_en      timestamptz NOT NULL DEFAULT now(),
  CHECK (estado_origen <> estado_destino)
);

-- ---------------------------------------------------------------------
-- 8. Existencias (saldo actual) y movimientos (kárdex)
-- ---------------------------------------------------------------------
-- Una fila por referencia + lote + estado. CHECK garantiza que nunca haya negativos.
CREATE TABLE existencia (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  referencia_id  integer NOT NULL REFERENCES referencia(id),
  lote_id        bigint  REFERENCES lote(id),
  estado         estado_inventario NOT NULL,
  cantidad       numeric(18,4) NOT NULL DEFAULT 0 CONSTRAINT ck_existencia_no_negativa CHECK (cantidad >= 0),
  actualizado_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE NULLS NOT DISTINCT (referencia_id, lote_id, estado)
);

-- Kárdex: registro inmutable, particionado por mes (volumen alto).
CREATE TABLE movimiento (
  id                     bigint GENERATED ALWAYS AS IDENTITY,
  fecha_efectiva         timestamptz NOT NULL,
  sync_uuid              uuid NOT NULL,
  tipo                   tipo_movimiento NOT NULL,
  referencia_id          integer NOT NULL REFERENCES referencia(id),
  lote_id                bigint  REFERENCES lote(id),
  estado_origen          estado_inventario,
  estado_destino         estado_inventario,
  cantidad_base          numeric(18,4) NOT NULL CHECK (cantidad_base > 0),
  costo_unitario_base    numeric(18,4) NOT NULL DEFAULT 0 CHECK (costo_unitario_base >= 0),
  saldo_total_despues    numeric(18,4),     -- calculado por el disparador
  costo_promedio_despues numeric(18,4),     -- calculado por el disparador
  documento_tipo         varchar(13) NOT NULL CHECK (documento_tipo IN ('COMPRA','VENTA','RESERVA','CAMBIO_ESTADO','MIGRACION')),
  documento_id           bigint NOT NULL,
  documento_detalle_id   bigint,
  reversa_movimiento_id  bigint,            -- movimiento anulado (id), si aplica
  periodo_id             integer NOT NULL,
  usuario_id             bigint NOT NULL REFERENCES usuario(id),
  dispositivo_id         smallint REFERENCES dispositivo(id),
  origen                 origen_operacion NOT NULL DEFAULT 'EN_LINEA',
  registrado_en          timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id, fecha_efectiva),
  UNIQUE (sync_uuid, fecha_efectiva),
  CONSTRAINT ck_mov_estados_segun_tipo CHECK (
    (tipo IN ('ENTRADA_COMPRA','SALDO_INICIAL','ANULACION_VENTA') AND estado_origen IS NULL AND estado_destino IS NOT NULL) OR
    (tipo IN ('SALIDA_VENTA','ANULACION_COMPRA')                  AND estado_origen IS NOT NULL AND estado_destino IS NULL) OR
    (tipo = 'CAMBIO_ESTADO' AND estado_origen IS NOT NULL AND estado_destino IS NOT NULL AND estado_origen <> estado_destino)
  ),
  CONSTRAINT ck_mov_no_vender_danado CHECK (tipo <> 'SALIDA_VENTA' OR estado_origen IN ('DISPONIBLE','RESERVADA')),  -- no se vende lo dañado
  CONSTRAINT ck_mov_entrada_es_compra CHECK (tipo <> 'ENTRADA_COMPRA' OR documento_tipo = 'COMPRA'),
  CONSTRAINT ck_mov_salida_es_venta CHECK (tipo <> 'SALIDA_VENTA' OR documento_tipo = 'VENTA'),
  CONSTRAINT ck_mov_anulacion_referencia CHECK (tipo NOT IN ('ANULACION_COMPRA','ANULACION_VENTA') OR reversa_movimiento_id IS NOT NULL)
) PARTITION BY RANGE (fecha_efectiva);

CREATE INDEX ix_mov_ref_fecha ON movimiento (referencia_id, fecha_efectiva);
CREATE INDEX ix_mov_documento ON movimiento (documento_tipo, documento_id);
CREATE INDEX ix_mov_periodo   ON movimiento (periodo_id);

CREATE TABLE movimiento_default PARTITION OF movimiento DEFAULT;   -- red de seguridad; las mensuales las crea fn_crear_particiones

-- Historial de costos y precios por referencia.
CREATE TABLE precio_historial (
  id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  referencia_id         integer NOT NULL REFERENCES referencia(id),
  costo_base            numeric(18,4) NOT NULL CHECK (costo_base >= 0),
  porcentaje_incremento numeric(6,2)  NOT NULL CHECK (porcentaje_incremento >= 0),
  precio_calculado      numeric(18,2) NOT NULL,
  precio_final          numeric(18,2) NOT NULL CHECK (precio_final >= 0),
  motivo                varchar(20) NOT NULL CHECK (motivo IN ('COMPRA','CAMBIO_PORCENTAJE','MANUAL','MIGRACION')),
  vigente_desde         timestamptz NOT NULL DEFAULT clock_timestamp(),
  vigente_hasta         timestamptz,
  usuario_id            bigint NOT NULL REFERENCES usuario(id),
  CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
);
CREATE UNIQUE INDEX ux_precio_vigente ON precio_historial (referencia_id) WHERE vigente_hasta IS NULL;

-- ---------------------------------------------------------------------
-- 9. Migración desde Excel (área de preparación)
-- ---------------------------------------------------------------------
CREATE TABLE importacion (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  archivo_nombre varchar(200) NOT NULL,
  archivo_sha256 char(64) NOT NULL UNIQUE,          -- impide cargar dos veces el mismo archivo
  fecha_corte    date NOT NULL,
  estado         varchar(12) NOT NULL DEFAULT 'RECIBIDA'
                 CHECK (estado IN ('RECIBIDA','VALIDADA','CARGADA','REVERTIDA','FALLIDA')),
  filas_total    integer NOT NULL DEFAULT 0,
  filas_validas  integer NOT NULL DEFAULT 0,
  filas_error    integer NOT NULL DEFAULT 0,
  usuario_id     bigint NOT NULL REFERENCES usuario(id),
  creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE importacion_fila (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  importacion_id  bigint   NOT NULL REFERENCES importacion(id) ON DELETE CASCADE,
  hoja            varchar(60) NOT NULL,         -- PRODUCTOS, TERCEROS, SALDOS...
  numero_fila     integer  NOT NULL,
  datos           jsonb    NOT NULL,            -- fila original, tal como llegó
  estado          varchar(10) NOT NULL DEFAULT 'PENDIENTE'
                  CHECK (estado IN ('PENDIENTE','VALIDA','ERROR','CARGADA','OMITIDA')),
  errores         jsonb,
  entidad_destino varchar(30),
  entidad_id      bigint,
  UNIQUE (importacion_id, hoja, numero_fila)
);
CREATE INDEX ix_imp_fila_estado ON importacion_fila (importacion_id, estado);

-- ---------------------------------------------------------------------
-- 10. Integración con sistemas externos y sincronización fuera de línea
-- ---------------------------------------------------------------------
CREATE TABLE sistema_externo (
  id     smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo varchar(30) NOT NULL UNIQUE,
  nombre varchar(100) NOT NULL,
  tipo   varchar(25) NOT NULL CHECK (tipo IN ('FACTURACION_ELECTRONICA','CONTABILIDAD','COMERCIO_ELECTRONICO','OTRO')),
  endpoint_url varchar(300),             -- URL que recibe los eventos (HTTP POST JSON)
  activo boolean NOT NULL DEFAULT true
);

CREATE TABLE mapeo_externo (
  sistema_id smallint NOT NULL REFERENCES sistema_externo(id),
  entidad    varchar(30) NOT NULL,    -- REFERENCIA, TERCERO, VENTA...
  id_interno bigint NOT NULL,
  id_externo varchar(100) NOT NULL,
  PRIMARY KEY (sistema_id, entidad, id_interno),
  UNIQUE (sistema_id, entidad, id_externo)
);

-- Bandeja de salida (patrón outbox): eventos que se envían con reintentos e idempotencia.
CREATE TABLE evento_integracion (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  clave_idempotencia uuid NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  sistema_id       smallint NOT NULL REFERENCES sistema_externo(id),
  direccion        varchar(7) NOT NULL CHECK (direccion IN ('SALIDA','ENTRADA')),
  tipo_evento      varchar(40) NOT NULL,      -- VENTA_EMITIDA, COMPRA_CONFIRMADA...
  entidad          varchar(30) NOT NULL,
  entidad_id       bigint NOT NULL,
  carga            jsonb NOT NULL,
  estado           varchar(10) NOT NULL DEFAULT 'PENDIENTE'
                   CHECK (estado IN ('PENDIENTE','ENVIADO','ERROR','DESCARTADO')),
  intentos         smallint NOT NULL DEFAULT 0,
  proximo_intento  timestamptz NOT NULL DEFAULT now(),
  ultimo_error     text,
  creado_en        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_evento_pendiente ON evento_integracion (proximo_intento) WHERE estado IN ('PENDIENTE','ERROR');

-- Operaciones recibidas desde equipos sin conexión. El uuid lo genera el equipo:
-- reenviar la misma operación no la aplica dos veces.
CREATE TABLE operacion_fuera_linea (
  uuid             uuid PRIMARY KEY,
  dispositivo_id   smallint NOT NULL REFERENCES dispositivo(id),
  usuario_id       bigint   NOT NULL REFERENCES usuario(id),
  tipo_operacion   varchar(20) NOT NULL CHECK (tipo_operacion IN ('VENTA','COMPRA','RESERVA','TERCERO')),
  carga            jsonb NOT NULL,
  creado_local_en  timestamptz NOT NULL,
  recibido_en      timestamptz NOT NULL DEFAULT now(),
  estado           varchar(10) NOT NULL DEFAULT 'RECIBIDA'
                   CHECK (estado IN ('RECIBIDA','APLICADA','CONFLICTO','RECHAZADA')),
  detalle_conflicto text,
  resuelto_por     bigint REFERENCES usuario(id),
  resuelto_en      timestamptz
);
CREATE INDEX ix_offline_estado ON operacion_fuera_linea (estado) WHERE estado IN ('RECIBIDA','CONFLICTO');

-- ---------------------------------------------------------------------
-- 11. Auditoría (append-only, particionada por mes)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
  id             bigint GENERATED ALWAYS AS IDENTITY,
  ocurrido_en    timestamptz NOT NULL DEFAULT now(),
  usuario_id     bigint,
  dispositivo_id smallint,
  direccion_ip   inet,
  origen         varchar(15),
  tabla          varchar(60) NOT NULL,
  registro_id    text NOT NULL,
  accion         varchar(10) NOT NULL CHECK (accion IN ('INSERT','UPDATE','DELETE','LOGIN','CIERRE','REAPERTURA','EXPORTAR')),
  antes          jsonb,
  despues        jsonb,
  PRIMARY KEY (id, ocurrido_en)
) PARTITION BY RANGE (ocurrido_en);
CREATE INDEX ix_auditoria_registro ON auditoria (tabla, registro_id);
CREATE INDEX ix_auditoria_usuario  ON auditoria (usuario_id, ocurrido_en);

CREATE TABLE auditoria_default PARTITION OF auditoria DEFAULT;

-- =====================================================================
-- 12. Reglas en la base de datos
-- =====================================================================

-- 12.1 Auditoría genérica: la aplicación fija app.usuario_id / app.dispositivo_id / app.origen por transacción.
CREATE FUNCTION fn_auditar() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  -- El hash de la contraseña nunca se copia a la auditoría.
  v_antes jsonb := CASE WHEN TG_OP IN ('UPDATE','DELETE') THEN to_jsonb(OLD) - 'hash_contrasena' END;
  v_desp  jsonb := CASE WHEN TG_OP IN ('INSERT','UPDATE') THEN to_jsonb(NEW) - 'hash_contrasena' END;
BEGIN
  INSERT INTO auditoria (usuario_id, dispositivo_id, origen, tabla, registro_id, accion, antes, despues)
  VALUES (nullif(current_setting('app.usuario_id', true), '')::bigint,
          nullif(current_setting('app.dispositivo_id', true), '')::smallint,
          nullif(current_setting('app.origen', true), ''),
          coalesce(TG_ARGV[0], TG_TABLE_NAME),
          -- id propio o, en tablas de clave compuesta (p. ej. rol_permiso), la fila completa
          coalesce(coalesce(v_desp, v_antes)->>'id', coalesce(v_desp, v_antes)->>'uuid', coalesce(v_desp, v_antes)::text),
          TG_OP, v_antes, v_desp);
  RETURN NULL;
END $$;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['usuario','usuario_rol','rol_permiso','producto','referencia','referencia_unidad',
                           'lote','tercero','consecutivo','periodo','compra','compra_detalle','venta',
                           'venta_detalle','reserva','cambio_estado','existencia','movimiento','precio_historial',
                           'importacion','operacion_fuera_linea']
  LOOP
    EXECUTE format('CREATE TRIGGER tg_auditar AFTER INSERT OR UPDATE OR DELETE ON %I
                    FOR EACH ROW EXECUTE FUNCTION fn_auditar(%L)', t, t);
  END LOOP;
END $$;

-- 12.2 Inmutabilidad de kárdex y auditoría.
CREATE FUNCTION fn_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La tabla % no admite % (registro inmutable; use un movimiento de anulación)', TG_TABLE_NAME, TG_OP;
END $$;
CREATE TRIGGER tg_mov_inmutable BEFORE UPDATE OR DELETE ON movimiento FOR EACH ROW EXECUTE FUNCTION fn_inmutable();
CREATE TRIGGER tg_aud_inmutable BEFORE UPDATE OR DELETE ON auditoria  FOR EACH ROW EXECUTE FUNCTION fn_inmutable();

-- 12.3a Períodos: crea el mes si no existe, con cierre programado el día 1 siguiente a las 02:00 (hora Colombia).
CREATE FUNCTION fn_asegurar_periodo(p_periodo integer) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_ini date := to_date(p_periodo::text || '01', 'YYYYMMDD');
BEGIN
  INSERT INTO periodo (id, fecha_inicio, fecha_fin, cierre_programado)
  VALUES (p_periodo, v_ini, (v_ini + interval '1 month - 1 day')::date,
          ((v_ini + interval '1 month')::timestamp + interval '2 hours') AT TIME ZONE 'America/Bogota')
  ON CONFLICT (id) DO NOTHING;
END $$;

-- 12.3b Particiones mensuales de kárdex y auditoría (se llama al migrar y cada mes desde la aplicación).
CREATE FUNCTION fn_crear_particiones(p_desde date, p_meses integer) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE
  v_mes date := date_trunc('month', p_desde)::date;
  v_creadas integer := 0;
  t text;
BEGIN
  FOR i IN 0 .. p_meses - 1 LOOP
    FOREACH t IN ARRAY ARRAY['movimiento','auditoria'] LOOP
      IF to_regclass(format('%I_%s', t, to_char(v_mes, 'YYYY_MM'))) IS NULL THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
          t || '_' || to_char(v_mes, 'YYYY_MM'), t,
          v_mes::text || ' 00:00 America/Bogota', (v_mes + interval '1 month')::date::text || ' 00:00 America/Bogota');
        v_creadas := v_creadas + 1;
      END IF;
    END LOOP;
    v_mes := (v_mes + interval '1 month')::date;
  END LOOP;
  RETURN v_creadas;
END $$;

-- 12.3 Aplicar movimiento: período abierto, lote obligatorio, sin negativos, costo promedio.
CREATE FUNCTION fn_aplicar_movimiento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_periodo   integer := to_char(NEW.fecha_efectiva AT TIME ZONE 'America/Bogota', 'YYYYMM')::integer;
  v_estado_p  varchar;
  v_ctrl_venc boolean;
  v_saldo_ant numeric(18,4);
  v_prom_ant  numeric(18,4);
  v_filas     integer;
BEGIN
  -- Período: no se registran movimientos en meses cerrados.
  SELECT estado INTO v_estado_p FROM periodo WHERE id = v_periodo;
  IF v_estado_p IS NULL THEN
    PERFORM fn_asegurar_periodo(v_periodo);
  ELSIF v_estado_p IN ('CERRADO','EN_CIERRE') THEN
    RAISE EXCEPTION 'El período % está % y no admite movimientos', v_periodo, v_estado_p;
  END IF;
  NEW.periodo_id := v_periodo;

  -- Productos con vencimiento exigen lote; se bloquea vender lotes vencidos.
  SELECT p.controla_vencimiento INTO v_ctrl_venc
    FROM referencia r JOIN producto p ON p.id = r.producto_id WHERE r.id = NEW.referencia_id;
  IF v_ctrl_venc AND NEW.lote_id IS NULL THEN
    RAISE EXCEPTION 'La referencia % controla vencimiento: el movimiento requiere lote', NEW.referencia_id;
  END IF;
  IF NEW.tipo = 'SALIDA_VENTA' AND NEW.lote_id IS NOT NULL AND EXISTS (
       SELECT 1 FROM lote WHERE id = NEW.lote_id AND fecha_vencimiento < (NEW.fecha_efectiva AT TIME ZONE 'America/Bogota')::date) THEN
    RAISE EXCEPTION 'El lote % está vencido y no puede venderse', NEW.lote_id;
  END IF;

  -- Bloqueo de la referencia: serializa movimientos concurrentes del mismo producto.
  SELECT costo_promedio INTO v_prom_ant FROM referencia WHERE id = NEW.referencia_id FOR UPDATE;
  SELECT coalesce(sum(cantidad), 0) INTO v_saldo_ant FROM existencia WHERE referencia_id = NEW.referencia_id;

  -- Restar del estado origen (el CHECK cantidad >= 0 impide negativos).
  IF NEW.estado_origen IS NOT NULL THEN
    UPDATE existencia SET cantidad = cantidad - NEW.cantidad_base, actualizado_en = now()
     WHERE referencia_id = NEW.referencia_id AND lote_id IS NOT DISTINCT FROM NEW.lote_id
       AND estado = NEW.estado_origen AND cantidad >= NEW.cantidad_base;
    GET DIAGNOSTICS v_filas = ROW_COUNT;
    IF v_filas = 0 THEN
      RAISE EXCEPTION 'Saldo % insuficiente para la referencia % (lote %): se pidieron %',
        NEW.estado_origen, NEW.referencia_id, coalesce(NEW.lote_id::text, 'sin lote'), NEW.cantidad_base
        USING ERRCODE = 'check_violation';
    END IF;
  END IF;

  -- Sumar al estado destino.
  IF NEW.estado_destino IS NOT NULL THEN
    INSERT INTO existencia (referencia_id, lote_id, estado, cantidad)
    VALUES (NEW.referencia_id, NEW.lote_id, NEW.estado_destino, NEW.cantidad_base)
    ON CONFLICT (referencia_id, lote_id, estado)
    DO UPDATE SET cantidad = existencia.cantidad + EXCLUDED.cantidad, actualizado_en = now();
  END IF;

  -- Costo promedio ponderado: cambia solo con entradas valorizadas.
  IF NEW.tipo IN ('ENTRADA_COMPRA','SALDO_INICIAL') THEN
    UPDATE referencia
       SET costo_promedio = CASE WHEN v_saldo_ant + NEW.cantidad_base = 0 THEN NEW.costo_unitario_base
                                 ELSE round((v_saldo_ant * v_prom_ant + NEW.cantidad_base * NEW.costo_unitario_base)
                                            / (v_saldo_ant + NEW.cantidad_base), 4) END,
           ultimo_costo   = NEW.costo_unitario_base
     WHERE id = NEW.referencia_id
     RETURNING costo_promedio INTO NEW.costo_promedio_despues;
  ELSE
    NEW.costo_promedio_despues := v_prom_ant;
    IF NEW.tipo IN ('SALIDA_VENTA','ANULACION_COMPRA') AND NEW.costo_unitario_base = 0 THEN
      NEW.costo_unitario_base := v_prom_ant;
    END IF;
  END IF;

  NEW.saldo_total_despues := v_saldo_ant
    + CASE WHEN NEW.estado_destino IS NOT NULL THEN NEW.cantidad_base ELSE 0 END
    - CASE WHEN NEW.estado_origen  IS NOT NULL THEN NEW.cantidad_base ELSE 0 END;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_aplicar_movimiento BEFORE INSERT ON movimiento FOR EACH ROW EXECUTE FUNCTION fn_aplicar_movimiento();

-- 12.4 Precio: costo × (1 + %/100), redondeado hacia arriba al múltiplo configurado. [ADOPTADO]
CREATE FUNCTION fn_calcular_precio(p_costo numeric, p_pct numeric, p_multiplo numeric)
RETURNS numeric LANGUAGE sql IMMUTABLE AS $$
  SELECT ceil(p_costo * (1 + p_pct / 100) / p_multiplo) * p_multiplo
$$;

-- Al confirmar una compra cambia el último costo; si el precio calculado cambia, se versiona.
CREATE FUNCTION fn_actualizar_precio(p_referencia integer, p_motivo varchar, p_usuario bigint)
RETURNS numeric LANGUAGE plpgsql AS $$
DECLARE r referencia%ROWTYPE; v_precio numeric(18,2);
BEGIN
  SELECT * INTO r FROM referencia WHERE id = p_referencia FOR UPDATE;
  v_precio := fn_calcular_precio(r.ultimo_costo, r.porcentaje_incremento, r.multiplo_redondeo);
  IF v_precio <> r.precio_venta THEN
    UPDATE precio_historial SET vigente_hasta = clock_timestamp() WHERE referencia_id = p_referencia AND vigente_hasta IS NULL;
    INSERT INTO precio_historial (referencia_id, costo_base, porcentaje_incremento, precio_calculado, precio_final, motivo, usuario_id)
    VALUES (p_referencia, r.ultimo_costo, r.porcentaje_incremento, v_precio, v_precio, p_motivo, p_usuario);
    UPDATE referencia SET precio_venta = v_precio WHERE id = p_referencia;
  END IF;
  RETURN v_precio;
END $$;

-- 12.5 Cierre mensual: verifica pendientes, fotografía saldos y bloquea el período.
CREATE FUNCTION fn_cerrar_periodo(p_periodo integer, p_usuario bigint) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE v_pend integer; v_filas integer;
BEGIN
  UPDATE periodo SET estado = 'EN_CIERRE' WHERE id = p_periodo AND estado IN ('ABIERTO','REABIERTO');
  IF NOT FOUND THEN RAISE EXCEPTION 'El período % no está abierto', p_periodo; END IF;

  SELECT count(*) INTO v_pend FROM operacion_fuera_linea
   WHERE estado IN ('RECIBIDA','CONFLICTO')
     AND to_char(creado_local_en AT TIME ZONE 'America/Bogota', 'YYYYMM')::integer <= p_periodo;
  IF v_pend > 0 THEN
    RAISE EXCEPTION 'Hay % operaciones fuera de línea sin resolver del período %', v_pend, p_periodo;
  END IF;

  DELETE FROM cierre_saldo WHERE periodo_id = p_periodo;   -- por si es un recierre
  -- Saldo al cierre = saldo actual − movimientos posteriores al período (por referencia, lote y estado).
  INSERT INTO cierre_saldo (periodo_id, referencia_id, lote_id, estado, cantidad, costo_promedio, valor_total)
  SELECT p_periodo, x.referencia_id, x.lote_id, x.estado, x.cantidad, c.costo,
         round(x.cantidad * c.costo, 2)
    FROM (
      SELECT e.referencia_id, e.lote_id, e.estado,
             e.cantidad - coalesce((
               SELECT sum(CASE WHEN m.estado_destino = e.estado THEN m.cantidad_base ELSE 0 END)
                    - sum(CASE WHEN m.estado_origen  = e.estado THEN m.cantidad_base ELSE 0 END)
                 FROM movimiento m
                WHERE m.referencia_id = e.referencia_id AND m.lote_id IS NOT DISTINCT FROM e.lote_id
                  AND m.periodo_id > p_periodo), 0) AS cantidad
        FROM existencia e) x
    CROSS JOIN LATERAL (   -- costo promedio vigente al final del período
      SELECT coalesce((SELECT m.costo_promedio_despues FROM movimiento m
                        WHERE m.referencia_id = x.referencia_id AND m.periodo_id <= p_periodo
                        ORDER BY m.fecha_efectiva DESC, m.id DESC LIMIT 1), 0) AS costo) c
   WHERE x.cantidad > 0;
  GET DIAGNOSTICS v_filas = ROW_COUNT;

  UPDATE periodo SET estado = 'CERRADO', cerrado_en = now(), cerrado_por = p_usuario WHERE id = p_periodo;
  INSERT INTO auditoria (usuario_id, tabla, registro_id, accion, despues)
  VALUES (p_usuario, 'periodo', p_periodo::text, 'CIERRE', jsonb_build_object('saldos', v_filas));
  RETURN v_filas;
END $$;

-- =====================================================================
-- 13. Vistas de consulta
-- =====================================================================
-- Existencias por referencia, separadas por estado (una sola bodega).
CREATE VIEW v_existencia_resumen AS
SELECT r.id AS referencia_id, r.codigo_barras, p.nombre AS producto, m.nombre AS marca, r.presentacion,
       u.codigo AS unidad_base,
       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DISPONIBLE'), 0) AS disponible,
       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'RESERVADA'), 0)  AS reservada,
       coalesce(sum(e.cantidad) FILTER (WHERE e.estado = 'DANADA'), 0)     AS danada,
       coalesce(sum(e.cantidad), 0) AS total,
       r.costo_promedio, r.precio_venta
  FROM referencia r
  JOIN producto p      ON p.id = r.producto_id
  JOIN marca m         ON m.id = r.marca_id
  JOIN unidad_medida u ON u.id = r.unidad_base_id
  LEFT JOIN existencia e ON e.referencia_id = r.id
 GROUP BY r.id, p.nombre, m.nombre, u.codigo;

-- Lotes con existencias y días para vencer.
CREATE VIEW v_lotes_por_vencer AS
SELECT l.id AS lote_id, r.codigo_barras, p.nombre AS producto, m.nombre AS marca, r.presentacion,
       l.codigo_lote, l.fecha_vencimiento,
       l.fecha_vencimiento - (now() AT TIME ZONE 'America/Bogota')::date AS dias_para_vencer,
       sum(e.cantidad) AS cantidad
  FROM lote l
  JOIN referencia r ON r.id = l.referencia_id
  JOIN producto p   ON p.id = r.producto_id
  JOIN marca m      ON m.id = r.marca_id
  JOIN existencia e ON e.lote_id = l.id AND e.cantidad > 0
 GROUP BY l.id, r.codigo_barras, p.nombre, m.nombre, r.presentacion;

-- =====================================================================
-- 14. Particiones iniciales: desde enero de 2026 hasta 6 meses después de hoy.
-- =====================================================================
SELECT fn_crear_particiones('2026-01-01',
  (extract(year FROM age(date_trunc('month', now()) + interval '6 months', '2026-01-01')) * 12
 + extract(month FROM age(date_trunc('month', now()) + interval '6 months', '2026-01-01')))::integer);
