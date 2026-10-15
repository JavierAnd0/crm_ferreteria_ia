import { useState } from 'react';
import { faFloppyDisk, faMagnifyingGlass, faPercent, faPlus } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { dinero, fecha, num, siNo } from '../formato.js';
import { Boton, Campo, Estado, Tabla, Ventana, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

const opciones = (lista, k = 'id', t = 'nombre') => lista.map(x => <option key={x[k]} value={x[k]}>{x[t]}</option>);

function Detalle({ id, alCambiarPrecio, alCerrar }) {
  const accion = useAccion();
  const avisar = useAvisar();
  const [d, recargarDetalle, error] = useCarga(async () => {
    const [ref, historial] = await Promise.all([api(`/api/referencias/${id}`), api(`/api/referencias/${id}/precio/historial`)]);
    return { ...ref, historial };
  }, [id]);
  const [pct, setPct] = useState('');
  const aplicar = accion(async () => {
    const r = await api(`/api/referencias/${id}/precio/porcentaje`, { metodo: 'PUT', cuerpo: { porcentaje: Number(pct) } });
    avisar(`Nuevo precio sin IVA: ${dinero(r.precioVenta)}`);
    alCambiarPrecio(); recargarDetalle(); setPct('');
  });
  return (
    <Ventana ancha titulo={d ? `${d.referencia.producto} ${d.referencia.marca} ${d.referencia.presentacion}` : 'Producto'} alCerrar={alCerrar}>
      <Estado datos={d} error={error}>{d && <>
      <p className="suave">Código {d.referencia.codigoBarras} · interno {d.referencia.codigoInterno} · unidad base {d.referencia.unidadBase}</p>
      <div className="resumen">
        <div><small>Disponible</small><strong>{num(d.referencia.disponible)}</strong></div>
        <div><small>Reservada</small><strong>{num(d.referencia.reservada)}</strong></div>
        <div><small>Dañada</small><strong>{num(d.referencia.danada)}</strong></div>
        <div><small>Stock mínimo</small><strong>{num(d.referencia.stockMinimo)}</strong></div>
        <div><small>Precio sin IVA</small><strong>{dinero(d.referencia.precioVenta)}</strong></div>
        {d.referencia.costoPromedio != null && <div><small>Costo promedio</small><strong>{dinero(d.referencia.costoPromedio)}</strong></div>}
      </div>
      <h2>Unidades</h2>
      <Tabla filas={d.unidades} columnas={[['unidad', 'Unidad'], ['factor', 'Factor a base', num, 'num'], ['usoCompra', 'Compra', siNo], ['usoVenta', 'Venta', siNo]]} />
      <h2>Lotes</h2>
      <Tabla filas={d.lotes} columnas={[['codigoLote', 'Lote'], ['fechaVencimiento', 'Vence', fecha], ['disponible', 'Disponible', num, 'num'],
        ['reservada', 'Reservada', num, 'num'], ['danada', 'Dañada', num, 'num']]} />
      <h2>Historial de precios</h2>
      <Tabla filas={d.historial} columnas={[['vigente_desde', 'Desde', fecha], ['costo_base', 'Costo', dinero, 'num'],
        ['porcentaje_incremento', '%', num, 'num'], ['precio_final', 'Precio', dinero, 'num'], ['motivo', 'Motivo'], ['usuario', 'Usuario']]} />
      {sesion.puede('PRECIO_CAMBIAR') && <div className="fila">
        <Campo etiqueta="Nuevo % de incremento"><input name="pct" type="number" step="any" value={pct} onChange={e => setPct(e.target.value)} /></Campo>
        <Boton data-a="pct" icono={faPercent} onClick={aplicar}>Aplicar</Boton></div>}
    </>}</Estado></Ventana>
  );
}

function NuevoProducto({ alGuardar, alCerrar }) {
  const accion = useAccion();
  const avisar = useAvisar();
  const [maestros] = useCarga(() => api('/api/catalogo/maestros'));
  const guardar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    const fraccion = Number(v.fraccionMinima || 1);
    await api('/api/productos', { metodo: 'POST', cuerpo: { codigoInterno: v.codigoInterno, nombre: v.nombre, categoriaId: Number(v.categoriaId),
      controlaVencimiento: v.vence === 'true', referencias: [{ marcaId: Number(v.marcaId), presentacion: v.presentacion,
        codigoBarras: v.codigoBarras, unidadBase: v.unidadBase, permiteFraccion: fraccion !== 1, fraccionMinima: fraccion,
        mesesGarantia: v.mesesGarantia ? Number(v.mesesGarantia) : null, tarifa: v.tarifa, porcentajeIncremento: Number(v.porcentajeIncremento),
        stockMinimo: v.stockMinimo ? Number(v.stockMinimo) : null,
        conversiones: v.unidadAlterna ? [{ unidad: v.unidadAlterna, factor: Number(v.factor), usoCompra: true, usoVenta: true }] : [] }] } });
    avisar('Producto registrado'); alGuardar(v.codigoBarras);
  });
  return (
    <Ventana titulo="Nuevo producto" alCerrar={alCerrar}>{maestros && (
      <form className="fila" id="nuevo" onSubmit={guardar}>
        <Campo etiqueta="Código interno"><input name="codigoInterno" required /></Campo>
        <Campo etiqueta="Nombre"><input name="nombre" required /></Campo>
        <Campo etiqueta="Categoría"><select name="categoriaId">{opciones(maestros.categorias)}</select></Campo>
        <Campo etiqueta="Controla vencimiento"><select name="vence"><option value="false">No</option><option value="true">Sí</option></select></Campo>
        <Campo etiqueta="Marca"><select name="marcaId">{opciones(maestros.marcas)}</select></Campo>
        <Campo etiqueta="Presentación"><input name="presentacion" required /></Campo>
        <Campo etiqueta="Código de barras"><input name="codigoBarras" required /></Campo>
        <Campo etiqueta="Unidad base"><select name="unidadBase">{opciones(maestros.unidades, 'codigo', 'codigo')}</select></Campo>
        <Campo etiqueta="Fracción mínima"><input name="fraccionMinima" type="number" step="any" defaultValue="1" /></Campo>
        <Campo etiqueta="Garantía (meses)"><input name="mesesGarantia" type="number" min="1" /></Campo>
        <Campo etiqueta="IVA"><select name="tarifa">{opciones(maestros.tarifas, 'codigo', 'codigo')}</select></Campo>
        <Campo etiqueta="% incremento"><input name="porcentajeIncremento" type="number" step="any" defaultValue="30" /></Campo>
        <Campo etiqueta="Stock mínimo"><input name="stockMinimo" type="number" step="any" /></Campo>
        <Campo etiqueta="Unidad alterna"><select name="unidadAlterna"><option value="">—</option>{opciones(maestros.unidades, 'codigo', 'codigo')}</select></Campo>
        <Campo etiqueta="Factor a base"><input name="factor" type="number" step="any" /></Campo>
        <Boton icono={faFloppyDisk}>Guardar</Boton>
      </form>)}
    </Ventana>
  );
}

export default function Productos() {
  const [q, setQ] = useState('');
  const [bajo, setBajo] = useState(false);
  const [consulta, setConsulta] = useState({ q: '', bajo: false });
  const [seleccion, setSeleccion] = useState(null);
  const [creando, setCreando] = useState(false);
  const [filas, recargar, error] = useCarga(
    () => api(`/api/referencias?q=${encodeURIComponent(consulta.q)}&bajoMinimo=${consulta.bajo}`), [consulta]);
  return (
    <>
      <div className="encabezado"><h1>Productos y existencias</h1>
        {sesion.puede('PRODUCTO_EDITAR') && <Boton data-a="nuevo" icono={faPlus} onClick={() => setCreando(true)}>Nuevo producto</Boton>}</div>
      <div className="panel fila">
        <Campo etiqueta="Buscar"><input name="q" size={30} value={q} onChange={e => setQ(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && setConsulta({ q, bajo })} /></Campo>
        <label><span><input type="checkbox" name="bajo" checked={bajo} onChange={e => setBajo(e.target.checked)} /> Solo bajo el mínimo</span></label>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={() => setConsulta({ q, bajo })}>Buscar</Boton>
      </div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['codigoBarras', 'Código', (v, f) => <a href="#" data-a="ver" onClick={e => { e.preventDefault(); setSeleccion(f.id); }}>{v}</a>],
          ['producto', 'Producto', (v, f) => <a href="#" onClick={e => { e.preventDefault(); setSeleccion(f.id); }}>{v}</a>], ['marca', 'Marca'], ['presentacion', 'Presentación'], ['unidadBase', 'Unidad'],
          ['disponible', 'Disponible', num, 'num'], ['reservada', 'Reservada', num, 'num'], ['danada', 'Dañada', num, 'num'],
          ['precioVenta', 'Precio sin IVA', dinero, 'num'], ['costoPromedio', 'Costo prom.', dinero, 'num'],
          ['mesesGarantia', 'Garantía', v => v ? `${v} meses` : ''], ['controlaVencimiento', 'Vence', siNo]]} />
      </Estado>
      {seleccion && <Detalle key={seleccion} id={seleccion} alCambiarPrecio={recargar} alCerrar={() => setSeleccion(null)} />}
      {creando && <NuevoProducto alCerrar={() => setCreando(false)}
        alGuardar={codigo => { setCreando(false); setQ(codigo); setConsulta({ q: codigo, bajo: false }); }} />}
    </>
  );
}
