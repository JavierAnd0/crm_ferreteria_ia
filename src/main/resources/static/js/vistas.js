// Vistas de la interfaz. Cada vista recibe el contenedor <main> y lo llena.
import { api, sesion } from './api.js';
import { local, pedirBloque, refrescarCatalogo, sincronizar, venderSinConexion, uuid } from './offline.js';

// ------------------------------------------------------------------ utilidades
export const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const dinero = v => v == null ? '' : Number(v).toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 2 });
const num = v => v == null ? '' : Number(v).toLocaleString('es-CO', { maximumFractionDigits: 4 });
const fecha = v => v ? new Date(v).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: v.length > 10 ? 'short' : undefined }) : '';
const hoy = () => new Date().toLocaleDateString('sv-SE');
const val = (raiz, nombre) => raiz.querySelector(`[name="${nombre}"]`)?.value.trim() ?? '';

export function avisar(mensaje, error = false) {
  const d = document.createElement('div');
  d.textContent = mensaje;
  if (error) d.className = 'error';
  document.querySelector('#aviso').append(d);
  setTimeout(() => d.remove(), error ? 7000 : 4000);
}

/** Tabla genérica: columnas = [[clave, título, formato?, 'num'?]] */
function tabla(filas, columnas, acciones) {
  if (!filas?.length) return '<p class="suave">Sin resultados.</p>';
  const cab = columnas.map(([, t, , c]) => `<th class="${c || ''}">${esc(t)}</th>`).join('') + (acciones ? '<th></th>' : '');
  const cuerpo = filas.map((f, i) => '<tr>' + columnas.map(([k, , fmt, c]) =>
    `<td class="${c || ''}">${fmt ? fmt(f[k], f) : esc(f[k])}</td>`).join('') + (acciones ? `<td>${acciones(f, i)}</td>` : '') + '</tr>').join('');
  return `<div class="tabla"><table><thead><tr>${cab}</tr></thead><tbody>${cuerpo}</tbody></table></div>`;
}

const conError = fn => async (...a) => { try { await fn(...a); } catch (e) { avisar(e.message, true); } };

// ------------------------------------------------------------------ vistas
async function login(main) {
  main.innerHTML = `<form class="panel login"><h1>Ingresar</h1>
    <p><label>Usuario<br><input name="login" autocomplete="username" required></label></p>
    <p><label>Contraseña<br><input name="contrasena" type="password" autocomplete="current-password" required></label></p>
    <button>Ingresar</button></form>`;
  main.querySelector('form').addEventListener('submit', conError(async e => {
    e.preventDefault();
    const s = await api('/api/auth/login', { metodo: 'POST', cuerpo: { login: val(main, 'login'), contrasena: val(main, 'contrasena') } });
    sesion.guardar(s);
    refrescarCatalogo().catch(() => {});
    location.hash = '#/inicio';
  }));
}

async function inicio(main) {
  const [bajos, lotes] = await Promise.all([
    api('/api/referencias?bajoMinimo=true&limite=50'), api('/api/lotes/por-vencer?dias=90')]);
  const ventas = sesion.puede('REPORTE_CONSULTAR') ? await api(`/api/reportes/ventas-diarias?desde=${hoy()}&hasta=${hoy()}`) : [];
  const total = ventas.reduce((s, v) => s + Number(v.total), 0);
  main.innerHTML = `<h1>Inicio</h1>
    <div class="tarjetas">
      <div class="tarjeta"><div class="suave">Ventas de hoy</div><div class="valor">${sesion.puede('REPORTE_CONSULTAR') ? dinero(total) : '—'}</div></div>
      <div class="tarjeta"><div class="suave">Referencias bajo el mínimo</div><div class="valor">${bajos.length}</div></div>
      <div class="tarjeta"><div class="suave">Lotes que vencen en 90 días</div><div class="valor">${lotes.length}</div></div>
      <div class="tarjeta"><div class="suave">Ventas sin sincronizar</div><div class="valor">${local.cola().length}</div></div>
    </div>
    <h2>Bajo el stock mínimo</h2>${tabla(bajos, [['codigoBarras', 'Código'], ['producto', 'Producto'], ['marca', 'Marca'],
      ['presentacion', 'Presentación'], ['disponible', 'Disponible', num, 'num'], ['stockMinimo', 'Mínimo', num, 'num']])}
    <h2>Próximos a vencer</h2>${tabla(lotes, [['codigo_lote', 'Lote'], ['producto', 'Producto'], ['marca', 'Marca'],
      ['fecha_vencimiento', 'Vence', fecha], ['dias_para_vencer', 'Días', null, 'num'], ['cantidad', 'Cantidad', num, 'num']])}`;
}

// --- Vender (punto de venta con lector de códigos y modo sin internet)
async function vender(main) {
  const lineas = [];
  let cliente = null;
  main.innerHTML = `<h1>Vender</h1>
    <div class="panel fila">
      <label>Cliente (cédula/NIT o nombre; vacío = consumidor final)<input name="cliente" size="30"></label>
      <button class="secundario" data-a="buscar-cliente">Buscar</button><span id="cliente-sel" class="suave">Consumidor final</span>
      <label>Forma de pago<select name="pago"><option>CONTADO</option><option>CREDITO</option></select></label>
    </div>
    <div class="panel"><div class="fila">
      <label>Código de barras o nombre (Enter)<input name="codigo" size="34" autofocus autocomplete="off"></label>
      <div id="sugerencias" class="suave"></div></div>
      <div id="lineas"></div>
      <div class="total" id="total"></div>
      <div class="fila"><span class="espacio"></span><button data-a="confirmar">Confirmar venta</button></div>
    </div><div id="resultado"></div>`;
  const pintar = () => {
    main.querySelector('#lineas').innerHTML = !lineas.length ? '<p class="suave">Escanee o escriba un producto para empezar.</p>' : tabla(lineas, [
      ['nombre', 'Referencia'],
      ['unidad', 'Unidad', (v, l) => `<select data-i="${lineas.indexOf(l)}" data-c="unidad">${l.unidades.map(u =>
        `<option ${u.unidad === v ? 'selected' : ''} value="${esc(u.unidad)}">${esc(u.unidad)} (×${num(u.factor)})</option>`).join('')}</select>`],
      ['cantidad', 'Cantidad', (v, l) => `<input data-i="${lineas.indexOf(l)}" data-c="cantidad" type="number" step="any" min="0" value="${v}" style="width:90px">`],
      ['precio', 'Precio', dinero, 'num'], ['subtotal', 'Subtotal', dinero, 'num']],
      (l, i) => `<button class="secundario" data-a="quitar" data-i="${i}">Quitar</button>`);
    const base = lineas.reduce((s, l) => s + l.subtotal, 0);
    const iva = lineas.reduce((s, l) => s + l.subtotal * l.iva / 100, 0);
    main.querySelector('#total').textContent = lineas.length ? `Total ${dinero(base + iva)} (IVA ${dinero(iva)})` : '';
  };
  const recalcular = l => {
    const u = l.unidades.find(x => x.unidad === l.unidad);
    l.factor = Number(u.factor);
    l.precio = Math.round(l.precioBase * l.factor * 100) / 100;
    l.subtotal = Math.round(l.precio * l.cantidad * 100) / 100;
  };
  const agregar = async r => {
    let unidades = [{ unidad: r.unidadBase, factor: 1, usoVenta: true }];
    try { unidades = (await api(`/api/referencias/${r.id}`)).unidades.filter(u => u.usoVenta); } catch { /* sin internet: unidad base */ }
    const l = { referenciaId: r.id, nombre: `${r.producto} ${r.marca} ${r.presentacion}`, unidades, unidad: r.unidadBase,
      cantidad: 1, precioBase: Number(r.precioVenta), iva: Number(r.porcentajeIva) };
    recalcular(l); lineas.push(l); pintar();
  };
  const campo = main.querySelector('[name=codigo]');
  campo.addEventListener('keydown', conError(async e => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const texto = campo.value.trim();
    if (!texto) return;
    let encontrados;
    try {
      encontrados = /^\d{8,14}$/.test(texto) ? [await api(`/api/referencias/codigo/${texto}`)] : await api(`/api/referencias?q=${encodeURIComponent(texto)}&limite=10`);
    } catch (err) {
      if (!err.sinRed && err.estado !== 404) throw err;
      encontrados = local.buscar(texto);
    }
    if (encontrados.length === 1) { await agregar(encontrados[0]); campo.value = ''; main.querySelector('#sugerencias').innerHTML = ''; return; }
    main.querySelector('#sugerencias').innerHTML = encontrados.length ? encontrados.map((r, i) =>
      `<button class="secundario" data-a="sugerencia" data-i="${i}">${esc(r.producto)} ${esc(r.marca)} ${esc(r.presentacion)} · ${num(r.disponible)} ${esc(r.unidadBase)}</button>`).join(' ')
      : 'No se encontró la referencia';
    main._sugerencias = encontrados;
  }));
  main.addEventListener('change', e => {
    const i = e.target.dataset.i, c = e.target.dataset.c;
    if (i === undefined || !c) return;
    lineas[i][c] = c === 'cantidad' ? Number(e.target.value) : e.target.value;
    recalcular(lineas[i]); pintar();
  });
  main.addEventListener('click', conError(async e => {
    const a = e.target.dataset.a;
    if (a === 'sugerencia') { await agregar(main._sugerencias[e.target.dataset.i]); campo.value = ''; main.querySelector('#sugerencias').innerHTML = ''; }
    if (a === 'quitar') { lineas.splice(Number(e.target.dataset.i), 1); pintar(); }
    if (a === 'buscar-cliente') {
      const r = await api(`/api/terceros?clientes=true&q=${encodeURIComponent(val(main, 'cliente'))}&limite=1`);
      cliente = r[0] || null;
      main.querySelector('#cliente-sel').textContent = cliente ? `${cliente.razonSocial || cliente.nombres + ' ' + cliente.apellidos}` : 'No encontrado: consumidor final';
    }
    if (a === 'confirmar') {
      if (!lineas.length) return avisar('Agregue al menos una referencia', true);
      const venta = { uuid: uuid(), clienteId: cliente?.id, formaPago: val(main, 'pago'),
        lineas: lineas.map(l => ({ referenciaId: l.referenciaId, unidad: l.unidad, cantidad: l.cantidad })) };
      e.target.disabled = true;
      try {
        const r = await api('/api/ventas', { metodo: 'POST', cuerpo: venta });
        main.querySelector('#resultado').innerHTML = `<div class="panel ok">Factura <a href="#/factura/${r.id}">${esc(r.factura)}</a> emitida por ${dinero(r.total)}.</div>`;
        lineas.length = 0; pintar();
      } catch (err) {
        if (!err.sinRed) throw err;
        const numero = venderSinConexion({ ...venta, lineas: lineas.map(l => ({ referenciaId: l.referenciaId, unidad: l.unidad,
          cantidad: l.cantidad, cantidadBase: l.cantidad * l.factor })) });
        window.dispatchEvent(new Event('cola-cambio'));
        main.querySelector('#resultado').innerHTML = `<div class="panel">Sin internet: factura <strong>${esc(numero)}</strong> guardada en este equipo; se enviará al recuperar la conexión.</div>`;
        lineas.length = 0; pintar();
      } finally { e.target.disabled = false; campo.focus(); }
    }
  }));
  pintar();
}

async function ventas(main) {
  main.innerHTML = `<h1>Facturas</h1><div class="panel fila">
    <label>Desde<input type="date" name="desde"></label><label>Hasta<input type="date" name="hasta" value="${hoy()}"></label>
    <button data-a="buscar">Buscar</button></div><div id="res"></div>`;
  const buscar = async () => {
    const filas = await api(`/api/ventas?desde=${val(main, 'desde')}&hasta=${val(main, 'hasta')}`);
    main.querySelector('#res').innerHTML = tabla(filas, [['factura', 'Factura', (v, f) => `<a href="#/factura/${f.id}">${esc(v)}</a>`],
      ['fecha_emision', 'Fecha', fecha], ['cliente', 'Cliente'], ['estado', 'Estado'], ['origen', 'Origen'], ['total', 'Total', dinero, 'num']]);
  };
  main.querySelector('[data-a=buscar]').addEventListener('click', conError(buscar));
  await buscar();
}

async function factura(main, id) {
  const { factura: f, lineas } = await api(`/api/ventas/${id}`);
  main.innerHTML = `<h1>Factura ${esc(f.factura)} <span class="suave">${esc(f.estado)}</span></h1>
    <div class="panel"><p><strong>Cliente:</strong> ${esc(f.cliente)} · ${esc(f.identificacion_cliente)}<br>
      <strong>Fecha:</strong> ${fecha(f.fecha_emision)} · <strong>Forma de pago:</strong> ${esc(f.forma_pago)} · <strong>Vendedor:</strong> ${esc(f.vendedor)}</p>
    ${tabla(lineas, [['referencia', 'Referencia'], ['unidad', 'Unidad'], ['cantidad', 'Cantidad', num, 'num'],
      ['precio_unitario', 'Precio', dinero, 'num'], ['valor_descuento', 'Descuento', dinero, 'num'], ['total_linea', 'Total', dinero, 'num'],
      ['codigo_lote', 'Lote'], ['fecha_fin_garantia', 'Garantía hasta', fecha]])}
    <p class="total">Subtotal ${dinero(f.subtotal)} · Descuento ${dinero(f.valor_descuento)} · IVA ${dinero(f.valor_impuestos)} · Total ${dinero(f.total)}</p>
    <div class="fila"><button onclick="print()">Imprimir</button>
      ${sesion.puede('VENTA_ANULAR') && f.estado === 'EMITIDA' ? '<button class="peligro" data-a="anular">Anular</button>' : ''}</div></div>`;
  main.querySelector('[data-a=anular]')?.addEventListener('click', conError(async () => {
    const motivo = prompt('Motivo de la anulación');
    if (!motivo) return;
    await api(`/api/ventas/${id}/anular`, { metodo: 'POST', cuerpo: { motivo } });
    avisar('Factura anulada; las existencias volvieron a disponible');
    await factura(main, id);
  }));
}

// --- Comprar (documento de ingreso)
async function comprar(main) {
  const lineas = [];
  const proveedores = await api('/api/terceros?proveedores=true&limite=200');
  main.innerHTML = `<h1>Comprar</h1>
    <div class="panel fila">
      <label>Proveedor<select name="proveedor">${proveedores.map(p => `<option value="${p.id}">${esc(p.razonSocial || p.nombres + ' ' + p.apellidos)}</option>`).join('')}</select></label>
      <label>N.º factura del proveedor<input name="factura"></label>
      <label>Fecha factura<input type="date" name="ffactura" value="${hoy()}"></label>
    </div>
    <div class="panel"><div class="fila">
      <label>Código de barras<input name="codigo" size="18"></label>
      <label>Unidad (vacío = base)<input name="unidad" size="8"></label>
      <label>Cantidad<input name="cantidad" type="number" step="any" min="0" style="width:90px"></label>
      <label>Costo unitario sin IVA<input name="costo" type="number" step="any" min="0" style="width:120px"></label>
      <label>Lote<input name="lote" size="10"></label>
      <label>Vence<input name="vence" type="date"></label>
      <button class="secundario" data-a="agregar">Agregar línea</button></div>
      <div id="lineas"></div>
      <div class="fila"><button data-a="confirmar">Confirmar compra</button></div></div><div id="resultado"></div>`;
  const pintar = () => { main.querySelector('#lineas').innerHTML = tabla(lineas, [['nombre', 'Referencia'], ['unidad', 'Unidad'],
    ['cantidad', 'Cantidad', num, 'num'], ['costoUnitario', 'Costo', dinero, 'num'], ['lote', 'Lote'], ['fechaVencimiento', 'Vence']],
    (l, i) => `<button class="secundario" data-a="quitar" data-i="${i}">Quitar</button>`); };
  main.addEventListener('click', conError(async e => {
    const a = e.target.dataset.a;
    if (a === 'agregar') {
      const r = await api(`/api/referencias/codigo/${encodeURIComponent(val(main, 'codigo'))}`);
      lineas.push({ referenciaId: r.id, nombre: `${r.producto} ${r.marca} ${r.presentacion}`, unidad: val(main, 'unidad') || null,
        cantidad: Number(val(main, 'cantidad')), costoUnitario: Number(val(main, 'costo')), lote: val(main, 'lote') || null,
        fechaVencimiento: val(main, 'vence') || null });
      ['codigo', 'unidad', 'cantidad', 'costo', 'lote', 'vence'].forEach(n => { main.querySelector(`[name=${n}]`).value = ''; });
      pintar();
    }
    if (a === 'quitar') { lineas.splice(Number(e.target.dataset.i), 1); pintar(); }
    if (a === 'confirmar') {
      const r = await api('/api/compras', { metodo: 'POST', cuerpo: { uuid: uuid(), proveedorId: Number(val(main, 'proveedor')),
        numeroFacturaProveedor: val(main, 'factura'), fechaFacturaProveedor: val(main, 'ffactura'),
        lineas: lineas.map(({ nombre, ...l }) => l) } });
      main.querySelector('#resultado').innerHTML = `<div class="panel ok">Documento de ingreso ${esc(r.documento)} por ${dinero(r.total)}. Precios actualizados.</div>`;
      lineas.length = 0; pintar();
    }
  }));
  pintar();
}

// --- Productos
async function productos(main) {
  const maestros = await api('/api/catalogo/maestros');
  const opciones = (lista, k = 'id', t = 'nombre') => lista.map(x => `<option value="${esc(x[k])}">${esc(x[t])}</option>`).join('');
  main.innerHTML = `<h1>Productos y existencias</h1>
    <div class="panel fila"><label>Buscar<input name="q" size="30"></label>
      <label><span><input type="checkbox" name="bajo"> Solo bajo el mínimo</span></label><button data-a="buscar">Buscar</button></div>
    <div id="res"></div><div id="detalle"></div>
    ${sesion.puede('PRODUCTO_EDITAR') ? `<details class="panel"><summary><strong>Nuevo producto</strong></summary><form class="fila" id="nuevo">
      <label>Código interno<input name="codigoInterno" required></label><label>Nombre<input name="nombre" required></label>
      <label>Categoría<select name="categoriaId">${opciones(maestros.categorias)}</select></label>
      <label>Controla vencimiento<select name="vence"><option value="false">No</option><option value="true">Sí</option></select></label>
      <label>Marca<select name="marcaId">${opciones(maestros.marcas)}</select></label>
      <label>Presentación<input name="presentacion" required></label><label>Código de barras<input name="codigoBarras" required></label>
      <label>Unidad base<select name="unidadBase">${opciones(maestros.unidades, 'codigo', 'codigo')}</select></label>
      <label>Fracción mínima<input name="fraccionMinima" type="number" step="any" value="1"></label>
      <label>Garantía (meses)<input name="mesesGarantia" type="number" min="1"></label>
      <label>IVA<select name="tarifa">${opciones(maestros.tarifas, 'codigo', 'codigo')}</select></label>
      <label>% incremento<input name="porcentajeIncremento" type="number" step="any" value="30"></label>
      <label>Stock mínimo<input name="stockMinimo" type="number" step="any"></label>
      <label>Unidad alterna<select name="unidadAlterna"><option value="">—</option>${opciones(maestros.unidades, 'codigo', 'codigo')}</select></label>
      <label>Factor a base<input name="factor" type="number" step="any"></label>
      <button>Guardar</button></form></details>` : ''}`;
  const buscar = async () => {
    const filas = await api(`/api/referencias?q=${encodeURIComponent(val(main, 'q'))}&bajoMinimo=${main.querySelector('[name=bajo]').checked}`);
    main.querySelector('#res').innerHTML = tabla(filas, [['codigoBarras', 'Código', (v, f) => `<a href="#" data-a="ver" data-id="${f.id}">${esc(v)}</a>`],
      ['producto', 'Producto'], ['marca', 'Marca'], ['presentacion', 'Presentación'], ['unidadBase', 'Unidad'],
      ['disponible', 'Disponible', num, 'num'], ['reservada', 'Reservada', num, 'num'], ['danada', 'Dañada', num, 'num'],
      ['precioVenta', 'Precio sin IVA', dinero, 'num'], ['costoPromedio', 'Costo prom.', dinero, 'num'],
      ['mesesGarantia', 'Garantía', v => v ? `${v} meses` : ''], ['controlaVencimiento', 'Vence', v => v ? 'Sí' : '']]);
  };
  main.addEventListener('click', conError(async e => {
    if (e.target.dataset.a === 'buscar') await buscar();
    if (e.target.dataset.a === 'ver') {
      e.preventDefault();
      const d = await api(`/api/referencias/${e.target.dataset.id}`);
      const historial = await api(`/api/referencias/${e.target.dataset.id}/precio/historial`);
      main.querySelector('#detalle').innerHTML = `<div class="panel"><h2>${esc(d.referencia.producto)} ${esc(d.referencia.marca)} ${esc(d.referencia.presentacion)}</h2>
        <h2>Unidades</h2>${tabla(d.unidades, [['unidad', 'Unidad'], ['factor', 'Factor a base', num, 'num'], ['usoCompra', 'Compra', v => v ? 'Sí' : ''], ['usoVenta', 'Venta', v => v ? 'Sí' : '']])}
        <h2>Lotes</h2>${tabla(d.lotes, [['codigoLote', 'Lote'], ['fechaVencimiento', 'Vence', fecha], ['disponible', 'Disponible', num, 'num'], ['reservada', 'Reservada', num, 'num'], ['danada', 'Dañada', num, 'num']])}
        <h2>Historial de precios</h2>${tabla(historial, [['vigente_desde', 'Desde', fecha], ['costo_base', 'Costo', dinero, 'num'], ['porcentaje_incremento', '%', num, 'num'], ['precio_final', 'Precio', dinero, 'num'], ['motivo', 'Motivo'], ['usuario', 'Usuario']])}
        ${sesion.puede('PRECIO_CAMBIAR') ? `<div class="fila"><label>Nuevo % de incremento<input name="pct" type="number" step="any"></label><button data-a="pct" data-id="${d.referencia.id}">Aplicar</button></div>` : ''}</div>`;
    }
    if (e.target.dataset.a === 'pct') {
      const r = await api(`/api/referencias/${e.target.dataset.id}/precio/porcentaje`, { metodo: 'PUT', cuerpo: { porcentaje: Number(val(main, 'pct')) } });
      avisar(`Nuevo precio sin IVA: ${dinero(r.precioVenta)}`); await buscar();
    }
  }));
  main.querySelector('#nuevo')?.addEventListener('submit', conError(async e => {
    e.preventDefault();
    const f = e.target, v = n => f.querySelector(`[name=${n}]`).value;
    const fraccion = Number(v('fraccionMinima') || 1);
    await api('/api/productos', { metodo: 'POST', cuerpo: { codigoInterno: v('codigoInterno'), nombre: v('nombre'), categoriaId: Number(v('categoriaId')),
      controlaVencimiento: v('vence') === 'true', referencias: [{ marcaId: Number(v('marcaId')), presentacion: v('presentacion'),
        codigoBarras: v('codigoBarras'), unidadBase: v('unidadBase'), permiteFraccion: fraccion !== 1, fraccionMinima: fraccion,
        mesesGarantia: v('mesesGarantia') ? Number(v('mesesGarantia')) : null, tarifa: v('tarifa'), porcentajeIncremento: Number(v('porcentajeIncremento')),
        stockMinimo: v('stockMinimo') ? Number(v('stockMinimo')) : null,
        conversiones: v('unidadAlterna') ? [{ unidad: v('unidadAlterna'), factor: Number(v('factor')), usoCompra: true, usoVenta: true }] : [] }] } });
    avisar('Producto registrado'); f.reset(); await buscar();
  }));
  await buscar();
}

// --- Terceros
async function terceros(main) {
  main.innerHTML = `<h1>Clientes y proveedores</h1>
    <div class="panel fila"><label>Buscar<input name="q"></label><button data-a="buscar">Buscar</button></div><div id="res"></div>
    ${sesion.puede('TERCERO_EDITAR') ? `<details class="panel"><summary><strong>Nuevo tercero</strong></summary><form class="fila" id="nuevo">
      <label>Tipo de persona<select name="tipoPersona"><option>NATURAL</option><option>JURIDICA</option></select></label>
      <label>Identificación<select name="tipoIdentificacion"><option>CC</option><option>NIT</option><option>CE</option><option>PAS</option></select></label>
      <label>Número (cédula o NIT)<input name="numeroIdentificacion" required></label><label>DV<input name="digitoVerificacion" size="2"></label>
      <label>Nombre<input name="nombres"></label><label>Apellido<input name="apellidos"></label><label>Razón social<input name="razonSocial"></label>
      <label>Dirección<input name="direccion"></label><label>Municipio<input name="municipio"></label>
      <label>Teléfono<input name="telefono"></label><label>Correo<input name="correo" type="email"></label>
      <label><span><input type="checkbox" name="esCliente" checked> Cliente</span></label>
      <label><span><input type="checkbox" name="esProveedor"> Proveedor</span></label><button>Guardar</button></form></details>` : ''}`;
  const buscar = async () => {
    const filas = await api(`/api/terceros?q=${encodeURIComponent(val(main, 'q'))}`);
    main.querySelector('#res').innerHTML = tabla(filas, [['tipoIdentificacion', 'Tipo'], ['numeroIdentificacion', 'Número', (v, f) => esc(v) + (f.digitoVerificacion != null ? '-' + f.digitoVerificacion : '')],
      ['razonSocial', 'Nombre o razón social', (v, f) => esc(v || `${f.nombres} ${f.apellidos}`)], ['telefono', 'Teléfono'], ['correo', 'Correo'],
      ['esCliente', 'Cliente', v => v ? 'Sí' : ''], ['esProveedor', 'Proveedor', v => v ? 'Sí' : '']]);
  };
  main.querySelector('[data-a=buscar]').addEventListener('click', conError(buscar));
  main.querySelector('#nuevo')?.addEventListener('submit', conError(async e => {
    e.preventDefault();
    const f = e.target, v = n => f.querySelector(`[name=${n}]`).value.trim() || null;
    await api('/api/terceros', { metodo: 'POST', cuerpo: { tipoPersona: v('tipoPersona'), tipoIdentificacion: v('tipoIdentificacion'),
      numeroIdentificacion: v('numeroIdentificacion'), digitoVerificacion: v('digitoVerificacion') ? Number(v('digitoVerificacion')) : null,
      nombres: v('nombres'), apellidos: v('apellidos'), razonSocial: v('razonSocial'), direccion: v('direccion'), municipio: v('municipio'),
      telefono: v('telefono'), correo: v('correo'), esCliente: f.esCliente.checked, esProveedor: f.esProveedor.checked } });
    avisar('Tercero registrado'); f.reset(); await buscar();
  }));
  await buscar();
}

// --- Reservas y dañados
async function reservas(main) {
  main.innerHTML = `<h1>Reservas y dañados</h1>
    ${sesion.puede('RESERVA_GESTIONAR') ? `<form class="panel fila" id="reservar"><strong>Reservar</strong>
      <label>Código de barras<input name="codigo" required></label><label>Cantidad (unidad base)<input name="cantidad" type="number" step="any" required></label>
      <label>Cédula/NIT del cliente<input name="cliente" required></label><button>Reservar</button></form>` : ''}
    ${sesion.puede('ESTADO_DANADO') ? `<form class="panel fila" id="danado"><strong>Dañados</strong>
      <label>Código de barras<input name="codigo" required></label><label>Id de lote (si aplica)<input name="lote" size="6"></label>
      <label>Cantidad<input name="cantidad" type="number" step="any" required></label>
      <label>Acción<select name="accion"><option value="DANAR">Marcar dañado</option><option value="RECUPERAR">Recuperar</option></select></label>
      <label>Motivo<input name="motivo" required></label><button>Registrar</button></form>` : ''}
    <h2>Reservas activas</h2><div id="res"></div>`;
  const listar = async () => {
    main.querySelector('#res').innerHTML = tabla(await api('/api/reservas'), [['id', 'N.º'], ['referencia', 'Referencia'], ['codigo_lote', 'Lote'],
      ['cantidad_base', 'Cantidad', num, 'num'], ['cliente', 'Cliente'], ['vence_en', 'Vence', fecha]],
      f => sesion.puede('RESERVA_GESTIONAR') ? `<button class="secundario" data-a="liberar" data-id="${f.id}">Liberar</button>` : '');
  };
  main.addEventListener('click', conError(async e => {
    if (e.target.dataset.a !== 'liberar') return;
    await api(`/api/reservas/${e.target.dataset.id}/liberar`, { metodo: 'POST' }); avisar('Reserva liberada'); await listar();
  }));
  main.querySelector('#reservar')?.addEventListener('submit', conError(async e => {
    e.preventDefault();
    const r = await api(`/api/referencias/codigo/${encodeURIComponent(val(e.target, 'codigo'))}`);
    const c = (await api(`/api/terceros?clientes=true&q=${encodeURIComponent(val(e.target, 'cliente'))}&limite=1`))[0];
    if (!c) throw new Error('Cliente no encontrado');
    await api('/api/reservas', { metodo: 'POST', cuerpo: { uuid: uuid(), clienteId: c.id, referenciaId: r.id, cantidadBase: Number(val(e.target, 'cantidad')) } });
    avisar('Reserva registrada'); e.target.reset(); await listar();
  }));
  main.querySelector('#danado')?.addEventListener('submit', conError(async e => {
    e.preventDefault();
    const r = await api(`/api/referencias/codigo/${encodeURIComponent(val(e.target, 'codigo'))}`);
    await api('/api/existencias/cambios-estado', { metodo: 'POST', cuerpo: { referenciaId: r.id, loteId: val(e.target, 'lote') ? Number(val(e.target, 'lote')) : null,
      cantidadBase: Number(val(e.target, 'cantidad')), accion: val(e.target, 'accion'), motivo: val(e.target, 'motivo') } });
    avisar('Cambio de estado registrado'); e.target.reset();
  }));
  await listar();
}

async function kardex(main) {
  main.innerHTML = `<h1>Kárdex</h1><div class="panel fila"><label>Código de barras (vacío = todos)<input name="codigo"></label>
    <label>Desde<input type="date" name="desde"></label><label>Hasta<input type="date" name="hasta" value="${hoy()}"></label>
    <button data-a="buscar">Consultar</button></div><div id="res"></div>`;
  main.querySelector('[data-a=buscar]').addEventListener('click', conError(async () => {
    let ref = '';
    if (val(main, 'codigo')) ref = (await api(`/api/referencias/codigo/${encodeURIComponent(val(main, 'codigo'))}`)).id;
    const filas = await api(`/api/kardex?referenciaId=${ref}&desde=${val(main, 'desde')}&hasta=${val(main, 'hasta')}`);
    main.querySelector('#res').innerHTML = tabla(filas, [['fecha_efectiva', 'Fecha', fecha], ['tipo', 'Tipo'], ['documento', 'Documento'],
      ['referencia', 'Referencia'], ['codigo_lote', 'Lote'], ['estado_origen', 'De'], ['estado_destino', 'A'],
      ['cantidad_base', 'Cantidad', num, 'num'], ['saldo_total_despues', 'Saldo', num, 'num'],
      ['costo_promedio_despues', 'Costo prom.', dinero, 'num'], ['usuario', 'Usuario'], ['origen', 'Origen']]);
  }));
}

async function cierres(main) {
  const listar = async () => {
    const filas = await api('/api/periodos');
    main.querySelector('#res').innerHTML = tabla(filas, [['id', 'Período'], ['estado', 'Estado'], ['cierre_programado', 'Cierre programado', fecha],
      ['cerrado_en', 'Cerrado', fecha], ['cerrado_por', 'Por'], ['motivo_reapertura', 'Motivo de reapertura']], f =>
      (sesion.puede('CIERRE_EJECUTAR') && f.estado !== 'CERRADO' ? `<button class="secundario" data-a="cerrar" data-id="${f.id}">Cerrar</button> ` : '')
      + (sesion.puede('CIERRE_REABRIR') && f.estado === 'CERRADO' ? `<button class="secundario" data-a="reabrir" data-id="${f.id}">Reabrir</button> ` : '')
      + (sesion.puede('COSTO_VER', 'REPORTE_CONSULTAR') && f.estado === 'CERRADO' ? `<button class="secundario" data-a="saldos" data-id="${f.id}">Saldos</button>` : ''));
  };
  main.innerHTML = `<h1>Cierres mensuales</h1><p class="suave">El cierre se ejecuta solo el día 1 de cada mes a las 02:00 (hora de Colombia). Un mes cerrado no admite movimientos.</p><div id="res"></div><div id="saldos"></div>`;
  main.addEventListener('click', conError(async e => {
    const { a, id } = e.target.dataset;
    if (a === 'cerrar') { const r = await api(`/api/periodos/${id}/cerrar`, { metodo: 'POST' }); avisar(`Período ${id} cerrado (${r.saldos} saldos)`); await listar(); }
    if (a === 'reabrir') { const motivo = prompt('Motivo de la reapertura'); if (!motivo) return; await api(`/api/periodos/${id}/reabrir`, { metodo: 'POST', cuerpo: { motivo } }); await listar(); }
    if (a === 'saldos') main.querySelector('#saldos').innerHTML = `<h2>Saldos al cierre ${esc(id)}</h2>` + tabla(await api(`/api/periodos/${id}/saldos`),
      [['referencia', 'Referencia'], ['codigo_lote', 'Lote'], ['estado', 'Estado'], ['cantidad', 'Cantidad', num, 'num'], ['costo_promedio', 'Costo', dinero, 'num'], ['valor_total', 'Valor', dinero, 'num']]);
  }));
  await listar();
}

async function reportes(main) {
  const [diarias, garantias] = await Promise.all([api('/api/reportes/ventas-diarias'), api('/api/reportes/garantias-vigentes')]);
  const costos = sesion.puede('COSTO_VER');
  const [margen, valoracion] = costos ? await Promise.all([api('/api/reportes/margen'), api('/api/reportes/valoracion')]) : [[], []];
  main.innerHTML = `<h1>Reportes</h1>
    <h2>Ventas de los últimos 30 días</h2>${tabla(diarias, [['dia', 'Día'], ['facturas', 'Facturas', null, 'num'], ['base', 'Base', dinero, 'num'], ['iva', 'IVA', dinero, 'num'], ['total', 'Total', dinero, 'num']])}
    ${costos ? `<h2>Margen por referencia (30 días)</h2>${tabla(margen, [['referencia', 'Referencia'], ['venta', 'Venta', dinero, 'num'], ['costo', 'Costo', dinero, 'num'], ['margen', 'Margen', dinero, 'num']])}
    <h2>Valoración del inventario (costo promedio)</h2>${tabla(valoracion, [['producto', 'Producto'], ['marca', 'Marca'], ['presentacion', 'Presentación'], ['total', 'Cantidad', num, 'num'], ['costo_promedio', 'Costo', dinero, 'num'], ['valor', 'Valor', dinero, 'num']])}` : ''}
    <h2>Garantías vigentes</h2>${tabla(garantias, [['factura', 'Factura'], ['cliente', 'Cliente'], ['referencia', 'Referencia'], ['fecha_fin_garantia', 'Hasta', fecha]])}`;
}

async function migracion(main) {
  main.innerHTML = `<h1>Migración desde Excel</h1>
    <div class="panel"><p>1. Descargue la plantilla, llene las hojas TERCEROS, PRODUCTOS y SALDOS.<br>2. Súbala: el sistema valida cada fila sin tocar el inventario.<br>3. Revise los errores y cargue las filas válidas.</p>
    <button class="secundario" data-a="plantilla">Descargar plantilla</button></div>
    <form class="panel fila" id="subir"><label>Archivo .xlsx<input type="file" name="archivo" accept=".xlsx" required></label>
      <label>Fecha de corte<input type="date" name="fechaCorte" value="${hoy()}" required></label><button>Validar</button></form>
    <div id="res"></div><h2>Importaciones</h2><div id="lista"></div>`;
  const listar = async () => { main.querySelector('#lista').innerHTML = tabla(await api('/api/migracion'), [['id', 'N.º'], ['archivo_nombre', 'Archivo'],
    ['fecha_corte', 'Corte'], ['estado', 'Estado'], ['filas_total', 'Filas', null, 'num'], ['filas_validas', 'Válidas', null, 'num'], ['filas_error', 'Errores', null, 'num']],
    f => f.estado === 'VALIDADA' ? `<button data-a="cargar" data-id="${f.id}">Cargar válidas</button>` : ''); };
  const detalle = async id => {
    const d = await api(`/api/migracion/${id}`);
    main.querySelector('#res').innerHTML = `<div class="panel"><strong>Importación ${d.resumen.id}: ${esc(d.resumen.estado)}</strong> · ${d.resumen.total} filas, ${d.resumen.validas} válidas, ${d.resumen.errores} con error, ${d.resumen.cargadas} cargadas`
      + tabla(d.filas.filter(f => f.estado === 'ERROR'), [['hoja', 'Hoja'], ['numero_fila', 'Fila'], ['errores', 'Errores', v => esc((v || []).join('; '))]]) + '</div>';
  };
  main.addEventListener('click', conError(async e => {
    if (e.target.dataset.a === 'plantilla') {
      const r = await api('/api/migracion/plantilla', { crudo: true });
      const a = document.createElement('a'); a.href = URL.createObjectURL(await r.blob()); a.download = 'plantilla_migracion.xlsx'; a.click();
    }
    if (e.target.dataset.a === 'cargar') { await api(`/api/migracion/${e.target.dataset.id}/cargar`, { metodo: 'POST' }); await detalle(e.target.dataset.id); await listar(); }
  }));
  main.querySelector('#subir').addEventListener('submit', conError(async e => {
    e.preventDefault();
    const r = await api('/api/migracion', { metodo: 'POST', formulario: new FormData(e.target) });
    await detalle(r.id); await listar();
  }));
  await listar();
}

async function auditoria(main) {
  main.innerHTML = `<h1>Auditoría</h1><div class="panel fila">
    <label>Desde<input type="date" name="desde"></label><label>Hasta<input type="date" name="hasta" value="${hoy()}"></label>
    <label>Tabla<input name="tabla" size="14"></label><label>Usuario<input name="usuario" size="12"></label>
    <label>Acción<select name="accion"><option value="">Todas</option><option>INSERT</option><option>UPDATE</option><option>DELETE</option><option>CIERRE</option><option>REAPERTURA</option></select></label>
    <button data-a="buscar">Consultar</button></div><div id="res"></div>`;
  main.querySelector('[data-a=buscar]').addEventListener('click', conError(async () => {
    const q = new URLSearchParams(['desde', 'hasta', 'tabla', 'usuario', 'accion'].map(n => [n, val(main, n)]));
    main.querySelector('#res').innerHTML = tabla(await api(`/api/auditoria?${q}`), [['ocurrido_en', 'Fecha', fecha], ['usuario', 'Usuario'], ['origen', 'Origen'],
      ['tabla', 'Tabla'], ['registro_id', 'Registro'], ['accion', 'Acción'],
      ['despues', 'Cambio', (v, f) => `<details><summary>ver</summary><pre>${esc(JSON.stringify({ antes: f.antes, despues: v }, null, 1))}</pre></details>`]]);
  }));
}

async function usuarios(main) {
  const roles = await api('/api/usuarios/roles');
  const listar = async () => { main.querySelector('#res').innerHTML = tabla(await api('/api/usuarios'), [['login', 'Usuario'], ['nombre', 'Nombre'],
    ['roles', 'Roles', v => esc(v.join(', '))], ['activo', 'Activo', v => v ? 'Sí' : 'No']],
    f => `<button class="secundario" data-a="activo" data-id="${f.id}" data-v="${!f.activo}">${f.activo ? 'Desactivar' : 'Activar'}</button>`); };
  main.innerHTML = `<h1>Usuarios</h1><form class="panel fila" id="nuevo"><label>Usuario<input name="login" required></label>
    <label>Nombre<input name="nombre" required></label><label>Contraseña (mín. 8)<input name="contrasena" type="password" minlength="8" required></label>
    <label>Rol<select name="rol">${roles.map(r => `<option value="${esc(r.codigo)}">${esc(r.nombre)}</option>`).join('')}</select></label><button>Crear</button></form><div id="res"></div>`;
  main.querySelector('#nuevo').addEventListener('submit', conError(async e => {
    e.preventDefault();
    await api('/api/usuarios', { metodo: 'POST', cuerpo: { login: val(e.target, 'login'), nombre: val(e.target, 'nombre'), contrasena: val(e.target, 'contrasena'), roles: [val(e.target, 'rol')] } });
    avisar('Usuario creado'); e.target.reset(); await listar();
  }));
  main.addEventListener('click', conError(async e => {
    if (e.target.dataset.a !== 'activo') return;
    await api(`/api/usuarios/${e.target.dataset.id}`, { metodo: 'PUT', cuerpo: { activo: e.target.dataset.v === 'true' } }); await listar();
  }));
  await listar();
}

async function offline(main) {
  const equipos = await api('/api/sync/dispositivos').catch(() => []);
  const pintar = () => {
    const e = local.equipo(), b = local.bloque();
    main.innerHTML = `<h1>Trabajar sin internet</h1>
      <div class="panel"><p>Este equipo guarda una copia del catálogo y un bloque de números de factura. Sin conexión, la venta se guarda aquí con un identificador único y se envía sola al volver internet. Si otra caja vendió la misma existencia, la venta queda en conflicto para revisión: nunca se generan saldos negativos.</p>
      <div class="fila"><label>Equipo<select name="equipo"><option value="">—</option>${equipos.filter(x => x.permite_fuera_linea)
        .map(x => `<option value="${x.id}" ${e?.id === x.id ? 'selected' : ''}>${esc(x.nombre)}</option>`).join('')}</select></label>
      <button data-a="bloque">Pedir 50 números</button><button class="secundario" data-a="catalogo">Actualizar catálogo local</button>
      <button class="secundario" data-a="sync">Sincronizar ahora</button></div>
      <p>Bloque actual: ${b ? `${esc(b.prefijo)} ${b.siguiente} a ${b.hasta} (${b.hasta - b.siguiente + 1} disponibles)` : 'ninguno'} · Catálogo local: ${local.catalogo().length} referencias</p></div>
      <h2>Ventas pendientes en este equipo</h2>${tabla(local.cola(), [['prefijo', 'Factura', (v, f) => `${esc(v)}-${f.numero}`], ['creadoLocalEn', 'Fecha', fecha], ['uuid', 'Identificador']])}
      <div id="conflictos"></div>`;
  };
  pintar();
  if (sesion.puede('PRODUCTO_EDITAR', 'VENTA_ANULAR')) {
    const c = await api('/api/sync/conflictos').catch(() => []);
    main.querySelector('#conflictos').innerHTML = '<h2>Conflictos por resolver</h2>' + tabla(c, [['dispositivo', 'Equipo'], ['usuario', 'Usuario'],
      ['creado_local_en', 'Fecha local', fecha], ['detalle_conflicto', 'Motivo']],
      f => `<button class="secundario" data-a="resolver" data-uuid="${esc(f.uuid)}">Resolver</button>`);
  }
  main.addEventListener('change', e => {
    if (e.target.name !== 'equipo') return;
    const x = equipos.find(q => String(q.id) === e.target.value);
    local.fijarEquipo(x ? { id: x.id, nombre: x.nombre } : null); pintar();
  });
  main.addEventListener('click', conError(async e => {
    const a = e.target.dataset.a;
    if (a === 'bloque') { const b = await pedirBloque(50); avisar(`Números ${b.prefijo} ${b.desde} a ${b.hasta} asignados`); }
    if (a === 'catalogo') { await refrescarCatalogo(); avisar('Catálogo local actualizado'); }
    if (a === 'resolver') {
      const resolucion = prompt('¿Cómo se resolvió? (por ejemplo: se anuló la factura en papel y se devolvió el dinero)');
      if (!resolucion) return;
      await api(`/api/sync/conflictos/${e.target.dataset.uuid}/resolver`, { metodo: 'POST', cuerpo: { resolucion } });
      avisar('Conflicto resuelto'); window.dispatchEvent(new HashChangeEvent('hashchange')); return;
    }
    if (a === 'sync') { const r = await sincronizar(); avisar(`${r.length} operación(es) procesadas`); window.dispatchEvent(new Event('cola-cambio')); }
    if (a) pintar();
  }));
}

export const VISTAS = {
  login, inicio, vender, ventas, comprar, productos, terceros, reservas, kardex, cierres, reportes, migracion, auditoria, usuarios, offline,
};
/** Resuelve la vista de una ruta, incluida la ruta con parámetro factura/{id}. */
export function vistaDe(ruta) {
  const m = ruta.match(/^factura\/(\d+)$/);
  return m ? main => factura(main, m[1]) : VISTAS[ruta];
}
