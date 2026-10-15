-- =====================================================================
-- V101 — Más datos de demostración (solo perfil «demo»): catálogo más amplio, proveedores y clientes,
-- cinco compras y dos semanas de ventas (1 al 14 de octubre de 2026), más dos existencias dañadas.
-- Igual que V100, el inventario solo se mueve insertando en «movimiento»: el disparador valida período,
-- lote, vencimiento y saldo, y calcula costo promedio. Compras y ventas se generan en orden cronológico
-- para que el kárdex muestre saldos coherentes con las fechas. Nombres, NIT y cédulas son ficticios.
-- =====================================================================
SELECT set_config('app.usuario_id', '1', false), set_config('app.origen', 'MIGRACION', false);

INSERT INTO categoria (nombre) VALUES
  ('Plomería'), ('Herramienta manual'), ('Cerrajería'), ('Seguridad industrial');
INSERT INTO marca (nombre) VALUES ('Stanley'), ('Truper'), ('Yale'), ('Gerfor'), ('3M'), ('Corona');

INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion, digito_verificacion,
                     nombres, apellidos, razon_social, direccion, municipio, telefono, correo, es_cliente, es_proveedor) VALUES
  ('JURIDICA','NIT','900555123',4, NULL, NULL, 'Ferreléctricos del Norte S.A.S.', 'Calle 80 # 45-12', 'Bogotá', '6015550404', 'pedidos@ferrelectricos-ejemplo.co', false, true),
  ('JURIDICA','NIT','901222333',9, NULL, NULL, 'Herramientas y Tornillos La 80 S.A.S.', 'Avenida Calle 80 # 70-05', 'Bogotá', '6015550505', 'ventas@herramientas-ejemplo.co', false, true),
  ('JURIDICA','NIT','860345678',4, NULL, NULL, 'Conjunto Residencial Los Pinos P.H.', 'Carrera 50 # 127-30', 'Bogotá', '6015550606', 'administracion@lospinos-ejemplo.co', true, false),
  ('JURIDICA','NIT','900777888',2, NULL, NULL, 'Mantenimientos Integrales Ejemplo S.A.S.', 'Calle 13 # 68-20', 'Bogotá', '6015550707', 'compras@mantenimientos-ejemplo.co', true, false),
  ('NATURAL', 'CC', '1020304050',NULL,'Carlos','Gómez Pardo', NULL, 'Calle 45 # 8-15', 'Bogotá', '3105550808', 'carlos.ejemplo@correo.co', true, false),
  ('NATURAL', 'CC', '52123456',  NULL,'Ana María','Rodríguez Silva', NULL, 'Carrera 15 # 93-60', 'Bogotá', '3155550909', 'ana.ejemplo@correo.co', true, false),
  ('NATURAL', 'CC', '79876543',  NULL,'Jorge','Ramírez Castro', NULL, 'Calle 26 Sur # 30-11', 'Bogotá', '3205551010', NULL, true, false),
  ('NATURAL', 'CC', '1098765432',NULL,'Diana','Suárez León', NULL, 'Transversal 60 # 115-40', 'Bogotá', '3015551111', 'diana.ejemplo@correo.co', true, false),
  ('NATURAL', 'CE', '456789',    NULL,'Luis','Pérez Navarro', NULL, 'Carrera 7 # 60-22', 'Bogotá', '3045551212', NULL, true, false);

INSERT INTO producto (codigo_interno, nombre, categoria_id, controla_vencimiento)
SELECT v.codigo, v.nombre, c.id, v.vence FROM (VALUES
  ('PLO-TUB-12',  'Tubo PVC presión 1/2" x 6 m',        'Plomería', false),
  ('PLO-COD-12',  'Codo PVC presión 1/2" x 90°',        'Plomería', false),
  ('PLO-LLA-JAR', 'Llave terminal para jardín 1/2"',    'Plomería', false),
  ('PLO-CIN-TEF', 'Cinta teflón 1/2" x 10 m',           'Plomería', false),
  ('HMA-MAR-16',  'Martillo de uña 16 oz',              'Herramienta manual', false),
  ('HMA-DES-JGO', 'Juego de destornilladores x 6',      'Herramienta manual', false),
  ('HMA-FLE-5',   'Flexómetro 5 m',                     'Herramienta manual', false),
  ('HMA-ALI-8',   'Alicate universal 8"',               'Herramienta manual', false),
  ('CER-CAN-40',  'Candado de latón 40 mm',             'Cerrajería', false),
  ('CER-POM-ALC', 'Cerradura de pomo para alcoba',      'Cerrajería', false),
  ('SEG-GUA-NIT', 'Guantes de nitrilo (par)',           'Seguridad industrial', false),
  ('SEG-GAF-CLA', 'Gafas de seguridad lente claro',     'Seguridad industrial', false),
  ('ADH-PEG-CER', 'Pegante para cerámica gris',         'Adhesivos y selladores', true),
  ('PIN-ESM-NEG', 'Esmalte sintético negro',            'Pinturas', true),
  ('PIN-THI',     'Thinner corriente',                  'Pinturas', false),
  ('ELE-TOM-DOB', 'Toma doble con polo a tierra',       'Material eléctrico', false),
  ('ELE-INT-SEN', 'Interruptor sencillo',               'Material eléctrico', false),
  ('ELE-CIN-AIS', 'Cinta aislante negra 3/4" x 10 m',   'Material eléctrico', false),
  ('ILU-PAN-18',  'Panel LED de sobreponer 18 W',       'Iluminación', false)
) AS v(codigo, nombre, cat, vence) JOIN categoria c ON c.nombre = v.cat;

INSERT INTO referencia (producto_id, marca_id, presentacion, codigo_barras, unidad_base_id,
                        permite_fraccion, fraccion_minima, meses_garantia, tarifa_impuesto_id, porcentaje_incremento, stock_minimo)
SELECT p.id, m.id, v.pres, v.barras, u.id, false, 1.0, v.gar, t.id, v.pct, v.minimo FROM (VALUES
  ('PLO-TUB-12', 'Pavco',    'Tramo 6 m',          '2990000000125', NULL::int, 35.0, 20.0),
  ('PLO-COD-12', 'Gerfor',   'Unidad',             '2990000000132', NULL, 60.0, 50.0),
  ('PLO-LLA-JAR','Corona',   'Unidad',             '2990000000149', 6,    40.0, 6.0),
  ('PLO-CIN-TEF','Genérica', 'Rollo',              '2990000000156', NULL, 80.0, 30.0),
  ('HMA-MAR-16', 'Stanley',  'Mango fibra',        '2990000000163', 12,   35.0, 4.0),
  ('HMA-MAR-16', 'Truper',   'Mango madera',       '2990000000170', 6,    35.0, 4.0),
  ('HMA-DES-JGO','Stanley',  'Estuche x 6',        '2990000000187', 12,   40.0, 3.0),
  ('HMA-FLE-5',  'Stanley',  'Cinta 19 mm',        '2990000000194', 6,    45.0, 6.0),
  ('HMA-ALI-8',  'Truper',   'Mango aislado',      '2990000000200', 6,    40.0, 4.0),
  ('CER-CAN-40', 'Yale',     'Blíster',            '2990000000217', 12,   40.0, 5.0),
  ('CER-POM-ALC','Yale',     'Acabado níquel',     '2990000000224', 12,   35.0, 3.0),
  ('SEG-GUA-NIT','3M',       'Talla M',            '2990000000231', NULL, 60.0, 24.0),
  ('SEG-GAF-CLA','3M',       'Unidad',             '2990000000248', NULL, 55.0, 10.0),
  ('ADH-PEG-CER','Corona',   'Bulto 25 kg',        '2990000000255', NULL, 20.0, 10.0),
  ('PIN-ESM-NEG','Pintuco',  'Galón 3,785 L',      '2990000000262', NULL, 30.0, 3.0),
  ('PIN-THI',    'Genérica', 'Galón 3,785 L',      '2990000000279', NULL, 35.0, 5.0),
  ('ELE-TOM-DOB','Genérica', 'Blanca 15 A',        '2990000000286', NULL, 50.0, 20.0),
  ('ELE-INT-SEN','Genérica', 'Blanco 15 A',        '2990000000293', NULL, 50.0, 20.0),
  ('ELE-CIN-AIS','3M',       'Rollo Temflex',      '2990000000309', NULL, 60.0, 20.0),
  ('ILU-PAN-18', 'Philips',  'Redondo luz blanca', '2990000000316', 12,   40.0, 6.0)
) AS v(prod, marca, pres, barras, gar, pct, minimo)
JOIN producto p ON p.codigo_interno = v.prod
JOIN marca m ON m.nombre = v.marca
JOIN unidad_medida u ON u.codigo = 'UND'
JOIN tarifa_impuesto t ON t.codigo = 'IVA19';

INSERT INTO referencia_unidad (referencia_id, unidad_id, factor_a_base)
SELECT r.id, r.unidad_base_id, 1 FROM referencia r
 WHERE NOT EXISTS (SELECT 1 FROM referencia_unidad ru WHERE ru.referencia_id = r.id AND ru.unidad_id = r.unidad_base_id);

INSERT INTO lote (referencia_id, codigo_lote, fecha_fabricacion, fecha_vencimiento)
SELECT r.id, v.lote, v.fab::date, v.vence::date FROM (VALUES
  ('2990000000255','CO2609',  '2026-09-10','2027-03-10'),
  ('2990000000262','PE2608',  '2026-08-20','2028-08-20'),
  ('2990000000019','PV2610B', '2026-10-01','2028-10-01'),
  ('2990000000057','AR2610',  '2026-10-06','2027-01-04'),
  ('2990000000040','SK2609',  '2026-09-05','2027-09-05')
) AS v(barras, lote, fab, vence) JOIN referencia r ON r.codigo_barras = v.barras;

-- Plan de compras: (orden, fecha, NIT proveedor, factura del proveedor, código de barras, cantidad, costo unitario, lote).
CREATE TEMPORARY TABLE plan_compra (orden int, fecha timestamptz, nit varchar, factura varchar,
                                    barras varchar, cantidad numeric, costo numeric, lote varchar) ON COMMIT DROP;
INSERT INTO plan_compra VALUES
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000125', 60, 21500, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000132', 200, 900, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000149', 18, 16800, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000156', 120, 1100, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000163', 10, 38500, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000170', 12, 21000, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000187', 8, 42000, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000194', 15, 17500, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000200', 10, 19800, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000217', 12, 23000, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000224', 6, 58000, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000231', 72, 3200, NULL),
  (1, '2026-10-01 08:30-05', '901222333', 'HT-10233', '2990000000248', 30, 6500, NULL),
  (2, '2026-10-02 10:15-05', '900555123', 'FN-5521',  '2990000000286', 80, 6900, NULL),
  (2, '2026-10-02 10:15-05', '900555123', 'FN-5521',  '2990000000293', 80, 5400, NULL),
  (2, '2026-10-02 10:15-05', '900555123', 'FN-5521',  '2990000000309', 100, 2600, NULL),
  (2, '2026-10-02 10:15-05', '900555123', 'FN-5521',  '2990000000316', 24, 24000, NULL),
  (2, '2026-10-02 10:15-05', '900555123', 'FN-5521',  '2990000000118', 36, 5000, NULL),
  (3, '2026-10-03 09:00-05', '900123456', 'FV-88412', '2990000000255', 40, 21000, 'CO2609'),
  (3, '2026-10-03 09:00-05', '900123456', 'FV-88412', '2990000000262', 10, 68000, 'PE2608'),
  (3, '2026-10-03 09:00-05', '900123456', 'FV-88412', '2990000000279', 20, 24000, NULL),
  (4, '2026-10-09 11:00-05', '900123456', 'FV-88690', '2990000000019', 12, 64500, 'PV2610B'),
  (4, '2026-10-09 11:00-05', '900123456', 'FV-88690', '2990000000040', 36, 12100, 'SK2609'),
  (4, '2026-10-09 11:00-05', '900123456', 'FV-88690', '2990000000057', 500, 640, 'AR2610'),
  (4, '2026-10-09 11:00-05', '900123456', 'FV-88690', '2990000000095', 400, 78, NULL),
  (4, '2026-10-09 11:00-05', '900123456', 'FV-88690', '2990000000125', 40, 22400, NULL),
  (5, '2026-10-09 15:30-05', '900555123', 'FN-5590',  '2990000000088', 200, 1850, NULL),
  (5, '2026-10-09 15:30-05', '900555123', 'FN-5590',  '2990000000118', 48, 4900, NULL);

-- Cantidades típicas por venta: paso (múltiplo en unidad base) y máximo de pasos.
CREATE TEMPORARY TABLE plan_cantidad (barras varchar PRIMARY KEY, paso numeric, max_pasos int) ON COMMIT DROP;
INSERT INTO plan_cantidad VALUES
  ('2990000000057', 50, 4),   -- cemento: bultos de 50 kg
  ('2990000000088', 5, 6),    -- cable: tramos de 5 m
  ('2990000000095', 50, 4),   -- tornillos: de a 50
  ('2990000000101', 0.5, 6),  -- puntillas: medias libras
  ('2990000000132', 2, 6),    -- codos
  ('2990000000156', 1, 4),
  ('2990000000231', 1, 6),
  ('2990000000286', 1, 6),
  ('2990000000293', 1, 6),
  ('2990000000309', 1, 4),
  ('2990000000118', 1, 6),
  ('2990000000125', 1, 5);

DO $$
DECLARE
  v_compras   bigint := (SELECT id FROM usuario WHERE login = 'compras1');
  v_vendedor  bigint := (SELECT id FROM usuario WHERE login = 'vendedor1');
  v_consumidor bigint := (SELECT id FROM tercero WHERE numero_identificacion = '222222222222');
  v_clientes  bigint[] := ARRAY(SELECT id FROM tercero WHERE es_cliente AND numero_identificacion <> '222222222222' ORDER BY id);
  v_ingreso   bigint := (SELECT numero_actual FROM consecutivo WHERE tipo_documento = 'INGRESO' AND dispositivo_id IS NULL);
  v_factura   bigint := (SELECT numero_actual FROM consecutivo WHERE tipo_documento = 'FACTURA' AND dispositivo_id IS NULL);
  v_dia date; v_hora timestamptz; v_n int; v_i int; v_lineas int; v_l int;
  v_compra bigint; v_venta bigint; v_det bigint; v_cliente bigint; v_pago varchar;
  c record; r record; v_lote bigint; v_disp numeric; v_cant numeric; v_paso numeric; v_max int; v_costo numeric;
BEGIN
  PERFORM setseed(0.2026);
  FOR v_dia IN SELECT generate_series('2026-10-01'::date, '2026-10-14'::date, '1 day')::date LOOP

    -- Compras del día (antes de abrir caja).
    FOR c IN SELECT DISTINCT orden, fecha, nit, factura FROM plan_compra
              WHERE (fecha AT TIME ZONE 'America/Bogota')::date = v_dia ORDER BY orden LOOP
      v_ingreso := v_ingreso + 1;
      INSERT INTO compra (uuid, prefijo, numero_ingreso, proveedor_id, numero_factura_proveedor, fecha_factura_proveedor,
                          fecha_efectiva, estado, usuario_id, confirmado_en)
      SELECT gen_random_uuid(), 'ING', v_ingreso, t.id, c.factura, v_dia, c.fecha, 'CONFIRMADA', v_compras, c.fecha + interval '10 minutes'
        FROM tercero t WHERE t.numero_identificacion = c.nit
      RETURNING id INTO v_compra;
      INSERT INTO compra_detalle (compra_id, linea, referencia_id, unidad_id, cantidad, factor_a_base, costo_unitario, porcentaje_impuesto, lote_id)
      SELECT v_compra, row_number() OVER (ORDER BY p.barras), r2.id, r2.unidad_base_id, p.cantidad, 1, p.costo, 19,
             (SELECT l.id FROM lote l WHERE l.referencia_id = r2.id AND l.codigo_lote = p.lote)
        FROM plan_compra p JOIN referencia r2 ON r2.codigo_barras = p.barras
       WHERE p.orden = c.orden AND p.nit = c.nit;
      UPDATE compra SET subtotal = d.sub, valor_impuestos = d.iva, total = d.sub + d.iva
        FROM (SELECT sum(total_linea) sub, round(sum(total_linea * porcentaje_impuesto / 100), 2) iva
                FROM compra_detalle WHERE compra_id = v_compra) d
       WHERE id = v_compra;
      INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_destino, cantidad_base,
                              costo_unitario_base, documento_tipo, documento_id, documento_detalle_id, usuario_id)
      SELECT c.fecha, gen_random_uuid(), 'ENTRADA_COMPRA', d.referencia_id, d.lote_id, 'DISPONIBLE', d.cantidad_base,
             round(d.costo_unitario / d.factor_a_base, 4), 'COMPRA', v_compra, d.id, v_compras
        FROM compra_detalle d WHERE d.compra_id = v_compra ORDER BY d.linea;
      PERFORM fn_actualizar_precio(referencia_id, 'COMPRA', v_compras) FROM compra_detalle WHERE compra_id = v_compra;
    END LOOP;

    -- Daños detectados en bodega.
    IF v_dia = '2026-10-10' THEN
      FOR r IN SELECT rf.id, e.lote_id, v.cant, v.motivo FROM (VALUES
                 ('2990000000118', 2.0, 'Bombillos rotos al desempacar la caja'),
                 ('2990000000040', 1.0, 'Cartucho perforado, se derrama')) AS v(barras, cant, motivo)
               JOIN referencia rf ON rf.codigo_barras = v.barras
               JOIN LATERAL (SELECT ex.lote_id FROM existencia ex LEFT JOIN lote lo ON lo.id = ex.lote_id
                              WHERE ex.referencia_id = rf.id AND ex.estado = 'DISPONIBLE' AND ex.cantidad >= v.cant
                              ORDER BY lo.fecha_vencimiento NULLS FIRST LIMIT 1) e ON true LOOP
        INSERT INTO cambio_estado (referencia_id, lote_id, estado_origen, estado_destino, cantidad_base, motivo, usuario_id)
        VALUES (r.id, r.lote_id, 'DISPONIBLE', 'DANADA', r.cant, r.motivo, (SELECT id FROM usuario WHERE login = 'inventario1'))
        RETURNING id INTO v_det;
        INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_origen, estado_destino,
                                cantidad_base, documento_tipo, documento_id, usuario_id)
        VALUES ((v_dia + time '07:45') AT TIME ZONE 'America/Bogota', gen_random_uuid(), 'CAMBIO_ESTADO', r.id, r.lote_id,
                'DISPONIBLE', 'DANADA', r.cant, 'CAMBIO_ESTADO', v_det, (SELECT id FROM usuario WHERE login = 'inventario1'));
      END LOOP;
    END IF;

    -- Ventas del día: de 3 a 8 facturas (menos el domingo), entre las 8:00 y las 18:00.
    v_n := CASE WHEN extract(isodow FROM v_dia) = 7 THEN 2 ELSE 3 + floor(random() * 6)::int END;
    FOR v_i IN 1..v_n LOOP
      v_hora := (v_dia + time '08:00') AT TIME ZONE 'America/Bogota'
                + make_interval(mins => ((v_i - 1) * 600 / v_n + floor(random() * (540 / v_n)))::int);
      IF random() < 0.6 THEN v_cliente := v_consumidor; v_pago := 'CONTADO';
      ELSE
        v_cliente := v_clientes[1 + floor(random() * array_length(v_clientes, 1))::int];
        v_pago := CASE WHEN random() < 0.35 THEN 'CREDITO' ELSE 'CONTADO' END;
      END IF;
      v_factura := v_factura + 1;
      INSERT INTO venta (uuid, prefijo, numero_factura, cliente_id, fecha_emision, forma_pago, estado, estado_envio_fe,
                         usuario_id, creado_en)
      VALUES (gen_random_uuid(), 'FE', v_factura, v_cliente, v_hora, v_pago, 'EMITIDA',
              CASE WHEN v_dia < '2026-10-14' THEN 'ACEPTADA' ELSE 'PENDIENTE' END, v_vendedor, v_hora)
      RETURNING id INTO v_venta;

      v_lineas := 1 + floor(random() * 4)::int;
      v_l := 0;
      -- Solo referencias que ya habían entrado al inventario antes de la hora de la venta.
      FOR r IN SELECT rf.id, rf.codigo_barras, rf.unidad_base_id, rf.precio_venta, rf.meses_garantia, t.porcentaje, rf.stock_minimo,
                      pr.controla_vencimiento
                 FROM referencia rf JOIN producto pr ON pr.id = rf.producto_id
                 JOIN tarifa_impuesto t ON t.id = rf.tarifa_impuesto_id
                WHERE rf.precio_venta > 0
                  AND EXISTS (SELECT 1 FROM movimiento m WHERE m.referencia_id = rf.id AND m.fecha_efectiva <= v_hora
                                 AND m.tipo IN ('ENTRADA_COMPRA','SALDO_INICIAL'))
                ORDER BY random() LIMIT v_lineas LOOP
        SELECT coalesce(pc.paso, 1), coalesce(pc.max_pasos, 3) INTO v_paso, v_max
          FROM (SELECT 1) x LEFT JOIN plan_cantidad pc ON pc.barras = r.codigo_barras;
        -- Lote: el que vence primero y no está vencido (FEFO); sin control de vencimiento, el saldo sin lote.
        SELECT ex.lote_id, ex.cantidad INTO v_lote, v_disp
          FROM existencia ex LEFT JOIN lote lo ON lo.id = ex.lote_id
         WHERE ex.referencia_id = r.id AND ex.estado = 'DISPONIBLE' AND ex.cantidad >= v_paso
           AND (NOT r.controla_vencimiento OR lo.fecha_vencimiento >= v_dia)
         ORDER BY lo.fecha_vencimiento NULLS FIRST LIMIT 1;
        CONTINUE WHEN v_disp IS NULL;
        -- Se deja siempre un remanente (la mitad del stock mínimo o dos pasos) para que haya existencias que mostrar
        -- y para que las pruebas que venden sobre los datos demo sigan teniendo saldo.
        v_cant := v_paso * least(1 + floor(random() * v_max)::int,
                                 floor((v_disp - greatest(2 * v_paso, coalesce(r.stock_minimo, 0) / 2)) / v_paso)::int);
        CONTINUE WHEN v_cant <= 0;
        v_l := v_l + 1;
        INSERT INTO venta_detalle (venta_id, linea, referencia_id, unidad_id, lote_id, cantidad, factor_a_base,
                                   precio_unitario, porcentaje_impuesto, fecha_fin_garantia)
        VALUES (v_venta, v_l, r.id, r.unidad_base_id, v_lote, v_cant, 1, r.precio_venta, r.porcentaje,
                CASE WHEN r.meses_garantia IS NULL THEN NULL ELSE (v_dia + make_interval(months => r.meses_garantia))::date END)
        RETURNING id INTO v_det;
        INSERT INTO movimiento (fecha_efectiva, sync_uuid, tipo, referencia_id, lote_id, estado_origen, cantidad_base,
                                documento_tipo, documento_id, documento_detalle_id, usuario_id)
        VALUES (v_hora, gen_random_uuid(), 'SALIDA_VENTA', r.id, v_lote, 'DISPONIBLE', v_cant, 'VENTA', v_venta, v_det, v_vendedor)
        RETURNING costo_unitario_base INTO v_costo;
        UPDATE venta_detalle SET costo_unitario_base = v_costo WHERE id = v_det;
        v_disp := NULL;
      END LOOP;

      IF v_l = 0 THEN   -- ninguna referencia elegida tenía saldo: se descarta la factura
        DELETE FROM venta WHERE id = v_venta;
        v_factura := v_factura - 1;
        CONTINUE;
      END IF;
      UPDATE venta SET subtotal = d.sub, valor_impuestos = d.iva, total = d.sub + d.iva
        FROM (SELECT sum(total_linea) sub, round(sum(total_linea * porcentaje_impuesto / 100), 2) iva
                FROM venta_detalle WHERE venta_id = v_venta) d
       WHERE id = v_venta;
    END LOOP;
  END LOOP;

  UPDATE consecutivo SET numero_actual = v_ingreso WHERE tipo_documento = 'INGRESO' AND dispositivo_id IS NULL;
  UPDATE consecutivo SET numero_actual = v_factura WHERE tipo_documento = 'FACTURA' AND dispositivo_id IS NULL;
END $$;
