// Ventana para imprimir etiquetas con código de barras de una o varias referencias.
import { useState } from 'react';
import { faPrint } from '@fortawesome/free-solid-svg-icons';
import { TAMANOS, imprimirEtiquetas, precioConIva } from './codigos.js';
import { dinero } from './formato.js';
import { Boton, Campo, CodigoBarras, Tabla, Ventana, useAccion } from './componentes.jsx';

const MAXIMO_VISTA = 12;

export default function VentanaEtiquetas({ referencias, alCerrar }) {
  const accion = useAccion();
  const [cantidades, setCantidades] = useState(() => Object.fromEntries(referencias.map(r => [r.id, 1])));
  const [conPrecio, setConPrecio] = useState(true);
  const [tamano, setTamano] = useState('carta');
  const items = referencias.map(r => ({ referencia: r, cantidad: Math.max(0, Math.min(500, Number(cantidades[r.id]) || 0)) }))
    .filter(i => i.cantidad > 0);
  const total = items.reduce((s, i) => s + i.cantidad, 0);
  const vista = items.flatMap(i => Array.from({ length: i.cantidad }, () => i.referencia)).slice(0, MAXIMO_VISTA);
  return (
    <Ventana ancha titulo={`Imprimir etiquetas (${total})`} alCerrar={alCerrar}>
      <div className="fila" id="etiquetas">
        <Campo etiqueta="Tamaño"><select name="tamano" value={tamano} onChange={e => setTamano(e.target.value)}>
          {Object.entries(TAMANOS).map(([k, t]) => <option key={k} value={k}>{t.nombre}</option>)}</select></Campo>
        <label><span><input type="checkbox" name="conPrecio" checked={conPrecio} onChange={e => setConPrecio(e.target.checked)} /> Mostrar precio con IVA</span></label>
        <span className="espacio" />
        <Boton data-a="imprimir" icono={faPrint} disabled={!total} onClick={accion(() => imprimirEtiquetas(items, { conPrecio, tamano }))}>Imprimir</Boton>
      </div>
      {referencias.length > 1 && <Tabla filas={referencias} columnas={[['producto', 'Producto', (v, r) => `${v} ${r.marca} ${r.presentacion}`],
        ['codigoBarras', 'Código', v => <span className="mono">{v}</span>],
        ['id', 'Copias', (v) => <input type="number" min="0" max="500" value={cantidades[v]} style={{ width: 90 }}
          onChange={e => setCantidades(c => ({ ...c, [v]: e.target.value }))} />, 'num']]} />}
      {referencias.length === 1 && <div className="fila" style={{ marginTop: '1rem' }}>
        <Campo etiqueta="Copias"><input name="copias" type="number" min="1" max="500" value={cantidades[referencias[0].id]}
          onChange={e => setCantidades({ [referencias[0].id]: e.target.value })} style={{ width: 110 }} /></Campo></div>}
      <h2>Vista previa{total > MAXIMO_VISTA ? ` (primeras ${MAXIMO_VISTA} de ${total})` : ''}</h2>
      <div className="vista-etiquetas">{vista.map((r, i) => (
        <div key={i} className="etiqueta-producto"><strong>{r.producto}</strong><span>{r.marca} · {r.presentacion}</span>
          <CodigoBarras codigo={r.codigoBarras} alto={34} ancho={1.6} fuente={12} />
          {conPrecio && <b>{dinero(precioConIva(r))}</b>}</div>))}</div>
    </Ventana>
  );
}
