-- =====================================================================
-- V100 — Datos de demostración (solo perfil «demo»).
-- Usuarios: admin, vendedor1, compras1, inventario1, gerente1 — contraseña Demo123*
-- Marcas ilustrativas; códigos de barras (prefijo 299), NIT, cédulas y precios ficticios.
-- =====================================================================
SELECT set_config('app.usuario_id', '1', false), set_config('app.origen', 'MIGRACION', false);

INSERT INTO usuario (login, nombre_completo, hash_contrasena) VALUES
  ('admin',       'Administrador del sistema', '$2a$10$/37NlyhKD9.l.naGbUsZwufk/FPMQfIE2eLP2SDYmbY/nPEinc5CG'),
  ('vendedor1',   'Vendedor de mostrador',     '$2a$10$/37NlyhKD9.l.naGbUsZwufk/FPMQfIE2eLP2SDYmbY/nPEinc5CG'),
  ('compras1',    'Auxiliar de compras',       '$2a$10$/37NlyhKD9.l.naGbUsZwufk/FPMQfIE2eLP2SDYmbY/nPEinc5CG'),
  ('inventario1', 'Responsable de inventario', '$2a$10$/37NlyhKD9.l.naGbUsZwufk/FPMQfIE2eLP2SDYmbY/nPEinc5CG'),
  ('gerente1',    'Gerencia',                  '$2a$10$/37NlyhKD9.l.naGbUsZwufk/FPMQfIE2eLP2SDYmbY/nPEinc5CG');
INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id FROM usuario u JOIN rol r ON
  (u.login, r.codigo) IN (('admin','ADMIN'), ('vendedor1','VENTAS'), ('compras1','COMPRAS'),
                          ('inventario1','INVENTARIO'), ('gerente1','GERENCIA'));

INSERT INTO dispositivo (nombre, permite_fuera_linea) VALUES ('CAJA-01', true), ('BODEGA-01', false);
INSERT INTO consecutivo (tipo_documento, prefijo, dispositivo_id, numero_desde, numero_hasta, numero_actual, resolucion)
SELECT 'FACTURA', 'FC1', id, 1, 5000, 0, 'Resolución DIAN por configurar' FROM dispositivo WHERE nombre = 'CAJA-01';

INSERT INTO categoria (nombre) VALUES
  ('Pinturas'), ('Adhesivos y selladores'), ('Construcción'), ('Herramienta eléctrica'),
  ('Material eléctrico'), ('Fijaciones'), ('Iluminación');
INSERT INTO marca (nombre) VALUES
  ('Pintuco'), ('Pavco'), ('Sika'), ('Argos'), ('Bosch'), ('DeWalt'), ('Centelsa'), ('Philips'), ('Genérica');

INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion, digito_verificacion,
                     nombres, apellidos, razon_social, direccion, municipio, telefono, correo, es_cliente, es_proveedor) VALUES
  ('JURIDICA','NIT','900123456',8, NULL, NULL, 'Distribuidora Ferretera Ejemplo S.A.S.', 'Calle 10 # 20-30', 'Bogotá', '6015550101', 'ventas@distribuidora-ejemplo.co', false, true),
  ('NATURAL', 'CC', '1012345678',NULL,'Laura','Martínez Ríos', NULL, 'Carrera 5 # 12-40', 'Bogotá', '3005550202', 'laura.ejemplo@correo.co', true, false),
  ('JURIDICA','NIT','901987654',0, NULL, NULL, 'Construcciones Ejemplo Ltda.', 'Avenida 68 # 1-50', 'Bogotá', '6015550303', 'compras@construcciones-ejemplo.co', true, true);

INSERT INTO producto (codigo_interno, nombre, categoria_id, controla_vencimiento)
SELECT v.codigo, v.nombre, c.id, v.vence FROM (VALUES
  ('PIN-VIN-BLA', 'Pintura vinilo tipo 1 blanca',     'Pinturas', true),
  ('ADH-PVC',     'Soldadura líquida para PVC',       'Adhesivos y selladores', true),
  ('SEL-SIL-TRA', 'Silicona acética transparente',    'Adhesivos y selladores', true),
  ('CON-CEM-GRI', 'Cemento gris uso general',         'Construcción', true),
  ('HER-TAL-PER', 'Taladro percutor 1/2" 650 W',      'Herramienta eléctrica', false),
  ('ELE-CAB-12',  'Cable THHN/THWN-2 12 AWG',         'Material eléctrico', false),
  ('FIJ-TOR-DRY', 'Tornillo drywall 6 x 1"',          'Fijaciones', false),
  ('FIJ-PUN-2',   'Puntilla con cabeza 2"',           'Fijaciones', false),
  ('ILU-LED-9W',  'Bombillo LED 9 W luz blanca E27',  'Iluminación', false)
) AS v(codigo, nombre, cat, vence) JOIN categoria c ON c.nombre = v.cat;

INSERT INTO referencia (producto_id, marca_id, presentacion, codigo_barras, unidad_base_id,
                        permite_fraccion, fraccion_minima, meses_garantia, tarifa_impuesto_id, porcentaje_incremento, stock_minimo)
SELECT p.id, m.id, v.pres, v.barras, u.id, v.frac, v.fmin, v.gar, t.id, v.pct, v.minimo FROM (VALUES
  ('PIN-VIN-BLA','Pintuco', 'Galón 3,785 L',    '2990000000019','UND', false, 1.0,  NULL::int, 30.0, 4.0),
  ('PIN-VIN-BLA','Pintuco', 'Cuñete 5 galones', '2990000000026','UND', false, 1.0,  NULL, 25.0, 1.0),
  ('ADH-PVC',    'Pavco',   'Tarro 1/4 galón',  '2990000000033','UND', false, 1.0,  NULL, 35.0, 6.0),
  ('SEL-SIL-TRA','Sika',    'Cartucho 280 ml',  '2990000000040','UND', false, 1.0,  NULL, 40.0, 10.0),
  ('CON-CEM-GRI','Argos',   'Bulto 50 kg',      '2990000000057','KG',  true,  1.0,  NULL, 15.0, 200.0),
  ('HER-TAL-PER','Bosch',   'GSB 13 RE',        '2990000000064','UND', false, 1.0,  12,   25.0, 1.0),
  ('HER-TAL-PER','DeWalt',  'DWD024',           '2990000000071','UND', false, 1.0,  36,   25.0, 1.0),
  ('ELE-CAB-12', 'Centelsa','Rollo 100 m',      '2990000000088','M',   true,  0.5,  NULL, 35.0, 100.0),
  ('FIJ-TOR-DRY','Genérica','Caja x 100',       '2990000000095','UND', false, 1.0,  NULL, 50.0, 200.0),
  ('FIJ-PUN-2',  'Genérica','Granel',           '2990000000101','KG',  true,  0.25, NULL, 40.0, 10.0),
  ('ILU-LED-9W', 'Philips', 'Bombillo E27',     '2990000000118','UND', false, 1.0,  12,   45.0, 12.0)
) AS v(prod, marca, pres, barras, unidad, frac, fmin, gar, pct, minimo)
JOIN producto p ON p.codigo_interno = v.prod
JOIN marca m ON m.nombre = v.marca
JOIN unidad_medida u ON u.codigo = v.unidad
JOIN tarifa_impuesto t ON t.codigo = 'IVA19';

INSERT INTO referencia_unidad (referencia_id, unidad_id, factor_a_base)
SELECT id, unidad_base_id, 1 FROM referencia;
INSERT INTO referencia_unidad (referencia_id, unidad_id, factor_a_base, uso_compra, uso_venta)
SELECT r.id, u.id, v.factor, v.compra, true FROM (VALUES
  ('2990000000057','BULTO', 50.0, true), ('2990000000088','ROLLO', 100.0, true),
  ('2990000000095','CAJA', 100.0, true), ('2990000000101','LB', 0.5, false)
) AS v(barras, unidad, factor, compra)
JOIN referencia r ON r.codigo_barras = v.barras JOIN unidad_medida u ON u.codigo = v.unidad;

-- Saldo inicial migrado al 30-sep-2026 y cierre de septiembre.
INSERT INTO importacion (archivo_nombre, archivo_sha256, fecha_corte, estado, filas_total, filas_validas, usuario_id)
VALUES ('inventario_inicial_demo.xlsx', repeat('0', 64), '2026-09-30', 'CARGADA', 2, 2, 1);
INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, estado_destino, cantidad_base,
                        costo_unitario_base, documento_tipo, documento_id, usuario_id, origen)
SELECT '2026-09-30 23:00-05', gen_random_uuid(), 'SALDO_INICIAL', r.id, 'DISPONIBLE', v.cant, v.costo, 'MIGRACION',
       (SELECT max(id) FROM importacion), 1, 'MIGRACION'
  FROM (VALUES ('2990000000064', 3.0, 280000.0), ('2990000000095', 500.0, 60.0)) AS v(barras, cant, costo)
  JOIN referencia r ON r.codigo_barras = v.barras;
SELECT fn_actualizar_precio(id, 'MIGRACION', 1) FROM referencia WHERE codigo_barras IN ('2990000000064','2990000000095');
SELECT fn_cerrar_periodo(202609, 1);

-- Compra (documento de ingreso ING-1) del 5-oct-2026 con lotes.
INSERT INTO lote (referencia_id, codigo_lote, fecha_fabricacion, fecha_vencimiento)
SELECT r.id, v.lote, v.fab::date, v.vence::date FROM (VALUES
  ('2990000000019','PV2609A','2026-09-01','2028-09-01'),
  ('2990000000033','SP2608', '2026-08-15','2027-02-15'),
  ('2990000000040','SK2607', '2026-07-10','2027-07-10'),
  ('2990000000057','AR2609', '2026-09-28','2026-12-27')
) AS v(barras, lote, fab, vence) JOIN referencia r ON r.codigo_barras = v.barras;

UPDATE consecutivo SET numero_actual = 1 WHERE tipo_documento = 'INGRESO' AND prefijo = 'ING';
INSERT INTO compra (uuid, prefijo, numero_ingreso, proveedor_id, numero_factura_proveedor, fecha_factura_proveedor,
                    fecha_efectiva, estado, usuario_id, confirmado_en)
SELECT gen_random_uuid(), 'ING', 1, t.id, 'FV-88231', '2026-10-05', '2026-10-05 09:30-05', 'CONFIRMADA', u.id, '2026-10-05 09:35-05'
  FROM tercero t, usuario u WHERE t.numero_identificacion = '900123456' AND u.login = 'compras1';

INSERT INTO compra_detalle (compra_id, linea, referencia_id, unidad_id, cantidad, factor_a_base, costo_unitario, porcentaje_impuesto, lote_id)
SELECT (SELECT max(id) FROM compra), v.linea, r.id, u.id, v.cant, ru.factor_a_base, v.costo, 19,
       (SELECT l.id FROM lote l WHERE l.referencia_id = r.id AND l.codigo_lote = v.lote)
  FROM (VALUES
    (1, '2990000000019', 'UND',   12.0, 62000.0,  'PV2609A'),
    (2, '2990000000033', 'UND',   24.0, 14500.0,  'SP2608'),
    (3, '2990000000040', 'UND',   36.0, 11800.0,  'SK2607'),
    (4, '2990000000057', 'BULTO', 10.0, 31000.0,  'AR2609'),
    (5, '2990000000071', 'UND',    2.0, 310000.0, NULL),
    (6, '2990000000088', 'ROLLO',  2.0, 180000.0, NULL),
    (7, '2990000000101', 'KG',    25.0, 8200.0,   NULL),
    (8, '2990000000118', 'UND',   48.0, 5200.0,   NULL),
    (9, '2990000000095', 'CAJA',   3.0, 7500.0,   NULL)
  ) AS v(linea, barras, unidad, cant, costo, lote)
  JOIN referencia r ON r.codigo_barras = v.barras
  JOIN unidad_medida u ON u.codigo = v.unidad
  JOIN referencia_unidad ru ON ru.referencia_id = r.id AND ru.unidad_id = u.id;

UPDATE compra c SET subtotal = d.sub, valor_impuestos = d.iva, total = d.sub + d.iva
  FROM (SELECT compra_id, sum(total_linea) sub, round(sum(total_linea * porcentaje_impuesto / 100), 2) iva
          FROM compra_detalle GROUP BY compra_id) d
 WHERE d.compra_id = c.id;

INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_destino, cantidad_base,
                        costo_unitario_base, documento_tipo, documento_id, documento_detalle_id, usuario_id)
SELECT c.fecha_efectiva, gen_random_uuid(), 'ENTRADA_COMPRA', d.referencia_id, d.lote_id, 'DISPONIBLE', d.cantidad_base,
       round(d.costo_unitario / d.factor_a_base, 4), 'COMPRA', c.id, d.id, c.usuario_id
  FROM compra c JOIN compra_detalle d ON d.compra_id = c.id ORDER BY d.linea;
SELECT fn_actualizar_precio(referencia_id, 'COMPRA', 1) FROM compra_detalle;
