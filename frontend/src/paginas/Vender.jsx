// Punto de venta: lector de códigos de barras, unidades de venta y modo sin internet.
import { useRef, useState } from 'react';
import { faBarcode, faBoxOpen, faCheck, faCircleCheck, faMagnifyingGlass, faTrash, faUser } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { local, uuid, venderSinConexion } from '../offline.js';
import { dinero, nombreTercero, num } from '../formato.js';
import { Avatar, Boton, Buscador, Campo, Etiqueta, Icono, Tabla, useAccion, useAvisar } from '../componentes.jsx';
import { precioConIva } from '../codigos.js';

/** Etiqueta de existencias para la lista de resultados: agotado, poco o disponible. */
const existencias = r => {
  const d = Number(r.disponible), m = Number(r.stockMinimo || 0);
  const tipo = d <= 0 ? 'error' : (m && d <= m) || d < 5 ? 'aviso' : 'ok';
  return <Etiqueta tipo={tipo}>{d <= 0 ? 'Agotado' : `${num(d)} ${r.unidadBase}`}</Etiqueta>;
};

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
  const [marcada, setMarcada] = useState(0);
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
    // Con resultados a la vista, las flechas recorren la lista y Enter agrega el marcado; Esc la cierra.
    if (sugerencias?.length && ['ArrowDown', 'ArrowUp'].includes(e.key)) {
      e.preventDefault();
      setMarcada(m => (m + (e.key === 'ArrowDown' ? 1 : sugerencias.length - 1)) % sugerencias.length);
      return;
    }
    if (e.key === 'Escape') { setSugerencias(null); return; }
    if (e.key !== 'Enter') return;
    e.preventDefault();
    if (sugerencias?.length && texto.trim() === sugerencias.consulta) return agregar(sugerencias[marcada]);
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
    else { setSugerencias(Object.assign(encontrados, { consulta: t })); setMarcada(0); }
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
      setResultado(<div className="panel resultado-ok"><Icono i={faCircleCheck} className="ok" /> Factura <a href={`#/factura/${r.id}`}>{r.factura}</a> emitida por {dinero(r.total)}.</div>);
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
      <div className="encabezado"><h1>Vender</h1></div>
      <div className="panel fila">
        <Campo etiqueta="Cliente (cédula/NIT o nombre; vacío = consumidor final)">
          <input name="cliente" size={30} value={busquedaCliente} onChange={e => setBusquedaCliente(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && buscarCliente()} /></Campo>
        <Boton clase="secundario" data-a="buscar-cliente" icono={faMagnifyingGlass} onClick={buscarCliente}>Buscar</Boton>
        <span className="celda-principal" style={{ minWidth: 0 }}><Avatar color="var(--info)"><Icono i={faUser} /></Avatar>
          <span><small>Cliente</small><span id="cliente-sel" className="nombre">{clienteTexto}</span></span></span>
        <span className="espacio" />
        <Campo etiqueta="Forma de pago">
          <select name="pago" value={pago} onChange={e => setPago(e.target.value)}><option>CONTADO</option><option>CREDITO</option></select></Campo>
      </div>
      <div className="panel">
        <div className="buscador-pos">
          <Campo etiqueta="Escanee el código de barras o escriba el nombre y presione Enter">
            <span className="buscador"><Icono i={faBarcode} />
              <input ref={campo} name="codigo" autoFocus autoComplete="off" placeholder="Ej.: 7701234567890 o «cemento»" value={texto}
                role="combobox" aria-expanded={!!sugerencias?.length} aria-controls="sugerencias"
                onChange={e => setTexto(e.target.value)} onKeyDown={buscar} /></span></Campo>
          <ul id="sugerencias" role="listbox">
            {sugerencias && (sugerencias.length ? sugerencias.map((r, i) => (
              <li key={r.id ?? i} role="option" aria-selected={i === marcada}>
                <button className={'sugerencia' + (i === marcada ? ' marcada' : '')} data-a="sugerencia"
                  onMouseEnter={() => setMarcada(i)} onClick={accion(() => agregar(r))}>
                  <Avatar cuadrado><Icono i={faBoxOpen} /></Avatar>
                  <span><span className="nombre">{r.producto}</span><small>{r.marca} · {r.presentacion} · <span className="mono">{r.codigoBarras}</span></small></span>
                  {existencias(r)}
                  <span className="precio">{dinero(precioConIva(r))}</span>
                </button></li>))
              : <li className="vacio suave">No se encontró la referencia «{sugerencias.consulta}».</li>)}
          </ul>
        </div>
        <div id="lineas" style={{ marginTop: '1.25rem' }}>
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
              acciones={(l, i) => <Boton clase="icono" data-a="quitar" icono={faTrash} aria-label="Quitar" title="Quitar"
                onClick={() => setLineas(ls => ls.filter((_, j) => j !== i))} />} />)}
        </div>
        <div className="pie-venta">
          <div className="total" id="total">{lineas.length ? <>Total {dinero(base + iva)}<small>IVA incluido {dinero(iva)}</small></> : ''}</div>
          <Boton data-a="confirmar" icono={faCheck} disabled={enviando} onClick={confirmar}>Confirmar venta</Boton></div>
      </div>
      <div id="resultado">{resultado}</div>
    </>
  );
}
