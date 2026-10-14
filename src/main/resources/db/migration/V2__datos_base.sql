-- =====================================================================
-- V2 — Datos base obligatorios: unidades, impuestos, roles, permisos,
-- consecutivos del servidor y tercero «Consumidor final».
-- El usuario administrador lo crea la aplicación al arrancar (variable ADMIN_PASSWORD).
-- =====================================================================
INSERT INTO unidad_medida (codigo, nombre, magnitud) VALUES
  ('UND','Unidad','CONTEO'), ('M','Metro','LONGITUD'), ('KG','Kilogramo','MASA'),
  ('LB','Libra (500 g)','MASA'), ('GL','Galón','VOLUMEN'), ('CAJA','Caja','EMPAQUE'),
  ('ROLLO','Rollo','EMPAQUE'), ('BULTO','Bulto','EMPAQUE');

INSERT INTO tarifa_impuesto (codigo, porcentaje) VALUES ('IVA19', 19), ('IVA5', 5), ('EXCLUIDO', 0);

INSERT INTO rol (codigo, nombre) VALUES
  ('ADMIN','Administrador'), ('INVENTARIO','Responsable de inventario'), ('COMPRAS','Compras'),
  ('VENTAS','Ventas'), ('GERENCIA','Gerencia');

INSERT INTO permiso (codigo, descripcion) VALUES
  ('USUARIO_ADMINISTRAR','Crear usuarios, asignar roles y desactivar cuentas'),
  ('PRODUCTO_EDITAR','Crear y modificar productos, referencias, unidades y conversiones'),
  ('TERCERO_EDITAR','Crear y modificar clientes y proveedores'),
  ('COMPRA_REGISTRAR','Registrar y confirmar compras (documento de ingreso)'),
  ('COMPRA_ANULAR','Anular un documento de ingreso confirmado'),
  ('VENTA_EMITIR','Registrar ventas y emitir factura'),
  ('VENTA_ANULAR','Anular una factura emitida'),
  ('RESERVA_GESTIONAR','Crear, liberar y consumir reservas'),
  ('ESTADO_DANADO','Clasificar existencias como dañadas o recuperarlas'),
  ('COSTO_VER','Ver costos y márgenes'),
  ('PRECIO_CAMBIAR','Cambiar porcentaje de incremento o fijar precio manual'),
  ('CIERRE_EJECUTAR','Ejecutar el cierre mensual'),
  ('CIERRE_REABRIR','Reabrir un período cerrado'),
  ('MIGRACION_EJECUTAR','Cargar datos desde Excel'),
  ('AUDITORIA_CONSULTAR','Consultar la auditoría'),
  ('REPORTE_CONSULTAR','Consultar reportes');

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM rol r JOIN permiso p ON
     r.codigo = 'ADMIN'
  OR (r.codigo = 'INVENTARIO' AND p.codigo IN ('PRODUCTO_EDITAR','RESERVA_GESTIONAR','ESTADO_DANADO','COSTO_VER','CIERRE_EJECUTAR','REPORTE_CONSULTAR'))
  OR (r.codigo = 'COMPRAS'    AND p.codigo IN ('TERCERO_EDITAR','COMPRA_REGISTRAR','COSTO_VER'))
  OR (r.codigo = 'VENTAS'     AND p.codigo IN ('TERCERO_EDITAR','VENTA_EMITIR','RESERVA_GESTIONAR'))
  OR (r.codigo = 'GERENCIA'   AND p.codigo IN ('COSTO_VER','PRECIO_CAMBIAR','COMPRA_ANULAR','VENTA_ANULAR','CIERRE_REABRIR','AUDITORIA_CONSULTAR','REPORTE_CONSULTAR'));

INSERT INTO consecutivo (tipo_documento, prefijo, dispositivo_id, numero_desde, numero_hasta, numero_actual, resolucion, vigente_hasta) VALUES
  ('INGRESO', 'ING', NULL, 1, 999999999, 0, NULL, NULL),
  ('FACTURA', 'FE',  NULL, 1, 999999999, 0, 'Resolución DIAN por configurar', NULL);

INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion, nombres, apellidos, es_cliente)
VALUES ('NATURAL', 'CC', '222222222222', 'Consumidor', 'Final', true);
