// Punto de venta: lector de códigos de barras, unidades de venta y modo sin internet.
import { useRef, useState } from 'react';
import { faBarcode, faCheck, faMagnifyingGlass, faTrash } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { local, uuid, venderSinConexion } from '../offline.js';
import { dinero, nombreTercero, num } from '../formato.js';
import { Boton, Campo, Icono, Tabla, useAccion, useAvisar } from '../componentes.jsx';

const conCalculo = l => {
  const factor = Number(l.unidades.find(x => x.unidad === l.unidad).factor);
  const precio = Math.round(l.precioBase * factor * 100) / 100;
  return { ...l, factor, precio, subtotal: Math.round(precio * l.cantidad * 100) / 100 };
};

export default function Vender() {
  const accion = useAccion();
  const avisar = useAvisar();
  const campo = useRef(null);
  const [lineas, setLineas] = useState([]);
  const [texto, setTexto] = useState('');
  const [sugerencias, setSugerencias] = useState(null);
  const [busquedaCliente, setBusquedaCliente] = useState('');
  const [cliente, setCliente] = useState(null);
  const [clienteTexto, setClienteTexto] = useState('Consumidor final');
  const [pago, setPago] = useState('CONTADO');
  const [resultado, setResultado] = useState(null);
  const [enviando, setEnviando] = useState(false);

  const agregar = async r => {
    let unidades = [{ unidad: r.unidadBase, factor: 1, usoVenta: true }];
    try { unidades = (await api(`/api/referencias/${r.id}`)).unidades.filter(u => u.usoVenta); } catch { /* sin internet: unidad base */ }
    setLineas(ls => [...ls, conCalculo({ referenciaId: r.id, nombre: `${r.producto} ${r.marca} ${r.presentacion}`, unidades,
      unidad: r.unidadBase, cantidad: 1, precioBase: Number(r.precioVenta), iva: Number(r.porcentajeIva) })]);
    setTexto(''); setSugerencias(null);
  };

  const buscar = accion(async e => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const t = texto.trim();
    if (!t) return;
    let encontrados;
    try {
      encontrados = /^\d{8,14}$/.test(t) ? [await api(`/api/referencias/codigo/${t}`)] : await api(`/api/referencias?q=${encodeURIComponent(t)}&limite=10`);
    } catch (err) {
      if (!err.sinRed && err.estado !== 404) throw err;
      encontrados = local.buscar(t);
    }
    if (encontrados.length === 1) await agregar(encontrados[0]);
    else setSugerencias(encontrados);
  });

  const cambiar = (i, campoLinea, valor) =>
    setLineas(ls => ls.map((l, j) => j === i ? conCalculo({ ...l, [campoLinea]: campoLinea === 'cantidad' ? Number(valor) : valor }) : l));

  const buscarCliente = accion(async () => {
    const r = await api(`/api/terceros?clientes=true&q=${encodeURIComponent(busquedaCliente)}&limite=1`);
    setCliente(r[0] || null);
    setClienteTexto(r[0] ? nombreTercero(r[0]) : 'No encontrado: consumidor final');
  });

  const confirmar = accion(async () => {
    if (!lineas.length) return avisar('Agregue al menos una referencia', true);
    const venta = { uuid: uuid(), clienteId: cliente?.id, formaPago: pago,
      lineas: lineas.map(l => ({ referenciaId: l.referenciaId, unidad: l.unidad, cantidad: l.cantidad })) };
    setEnviando(true);
    try {
      const r = await api('/api/ventas', { metodo: 'POST', cuerpo: venta });
      setResultado(<div className="panel ok"><Icono i={faCheck} /> Factura <a href={`#/factura/${r.id}`}>{r.factura}</a> emitida por {dinero(r.total)}.</div>);
      setLineas([]);
    } catch (err) {
      if (!err.sinRed) throw err;
      const numero = venderSinConexion({ ...venta, lineas: lineas.map(l => ({ referenciaId: l.referenciaId, unidad: l.unidad,
        cantidad: l.cantidad, cantidadBase: l.cantidad * l.factor })) });
      window.dispatchEvent(new Event('cola-cambio'));
      setResultado(<div className="panel">Sin internet: factura <strong>{numero}</strong> guardada en este equipo; se enviará al recuperar la conexión.</div>);
      setLineas([]);
    } finally { setEnviando(false); campo.current?.focus(); }
  });

  const base = lineas.reduce((s, l) => s + l.subtotal, 0);
  const iva = lineas.reduce((s, l) => s + l.subtotal * l.iva / 100, 0);

  return (
    <>
      <h1>Vender</h1>
      <div className="panel fila">
        <Campo etiqueta="Cliente (cédula/NIT o nombre; vacío = consumidor final)">
          <input name="cliente" size={30} value={busquedaCliente} onChange={e => setBusquedaCliente(e.target.value)} /></Campo>
        <Boton clase="secundario" data-a="buscar-cliente" icono={faMagnifyingGlass} onClick={buscarCliente}>Buscar</Boton>
        <span id="cliente-sel" className="suave">{clienteTexto}</span>
        <Campo etiqueta="Forma de pago">
          <select name="pago" value={pago} onChange={e => setPago(e.target.value)}><option>CONTADO</option><option>CREDITO</option></select></Campo>
      </div>
      <div className="panel">
        <div className="fila">
          <Campo etiqueta={<span><Icono i={faBarcode} /> Código de barras o nombre (Enter)</span>}>
            <input ref={campo} name="codigo" size={34} autoFocus autoComplete="off" value={texto}
              onChange={e => setTexto(e.target.value)} onKeyDown={buscar} /></Campo>
          <div id="sugerencias" className="suave">
            {sugerencias && (sugerencias.length ? sugerencias.map((r, i) => (
              <button key={r.id ?? i} className="secundario" data-a="sugerencia" onClick={accion(() => agregar(r))}>
                {r.producto} {r.marca} {r.presentacion} · {num(r.disponible)} {r.unidadBase}</button>))
              : 'No se encontró la referencia')}
          </div>
        </div>
        <div id="lineas">
          {!lineas.length ? <p className="suave">Escanee o escriba un producto para empezar.</p> : (
            <Tabla filas={lineas} columnas={[
              ['nombre', 'Referencia'],
              ['unidad', 'Unidad', (v, l) => (
                <select value={v} onChange={e => cambiar(lineas.indexOf(l), 'unidad', e.target.value)}>
                  {l.unidades.map(u => <option key={u.unidad} value={u.unidad}>{u.unidad} (×{num(u.factor)})</option>)}</select>)],
              ['cantidad', 'Cantidad', (v, l) => (
                <input type="number" step="any" min="0" value={v} style={{ width: 90 }}
                  onChange={e => cambiar(lineas.indexOf(l), 'cantidad', e.target.value)} />)],
              ['precio', 'Precio', dinero, 'num'], ['subtotal', 'Subtotal', dinero, 'num']]}
              acciones={(l, i) => <Boton clase="secundario" data-a="quitar" icono={faTrash} aria-label="Quitar"
                onClick={() => setLineas(ls => ls.filter((_, j) => j !== i))} />} />)}
        </div>
        <div className="total" id="total">{lineas.length ? `Total ${dinero(base + iva)} (IVA ${dinero(iva)})` : ''}</div>
        <div className="fila"><span className="espacio" />
          <Boton data-a="confirmar" icono={faCheck} disabled={enviando} onClick={confirmar}>Confirmar venta</Boton></div>
      </div>
      <div id="resultado">{resultado}</div>
    </>
  );
}
