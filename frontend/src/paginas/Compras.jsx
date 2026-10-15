// Historial de compras (documentos de ingreso): consulta por fechas, detalle en ventana y anulación.
import { useState } from 'react';
import { faBan, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { dinero, fecha, hoy, num } from '../formato.js';
import { Boton, Campo, Estado, Tabla, Ventana, useAccion, useAvisar, useCarga } from '../componentes.jsx';

function DetalleCompra({ id, alCerrar, alAnular }) {
  const accion = useAccion();
  const avisar = useAvisar();
  const [d, recargar, error] = useCarga(() => api(`/api/compras/${id}`), [id]);
  const anular = accion(async () => {
    const motivo = prompt('Motivo de la anulación');
    if (!motivo) return;
    await api(`/api/compras/${id}/anular`, { metodo: 'POST', cuerpo: { motivo } });
    avisar('Compra anulada; el inventario se reversó en el kárdex');
    recargar(); alAnular();
  });
  const c = d?.compra;
  return (
    <Ventana ancha titulo={c ? `Ingreso ${c.documento}` : 'Compra'} alCerrar={alCerrar}>
      <Estado datos={d} error={error}>{c && <>
        <p><strong>Proveedor:</strong> {c.proveedor} · {c.identificacion_proveedor}<br />
          <strong>Factura del proveedor:</strong> {c.numero_factura_proveedor || '—'} · <strong>Fecha:</strong> {fecha(c.fecha_efectiva)} · <strong>Estado:</strong> {c.estado}</p>
        <Tabla filas={d.lineas} columnas={[['codigo_barras', 'Código'], ['referencia', 'Referencia'], ['unidad', 'Unidad'],
          ['cantidad', 'Cantidad', num, 'num'], ['costo_unitario', 'Costo', dinero, 'num'], ['porcentaje_impuesto', 'IVA %', num, 'num'],
          ['total_linea', 'Total', dinero, 'num'], ['codigo_lote', 'Lote'], ['fecha_vencimiento', 'Vence', fecha]]} />
        <p className="total">Subtotal {dinero(c.subtotal)} · IVA {dinero(c.valor_impuestos)} · Total {dinero(c.total)}</p>
        {sesion.puede('COMPRA_ANULAR') && c.estado !== 'ANULADA' &&
          <div className="fila"><Boton clase="peligro" data-a="anular" icono={faBan} onClick={anular}>Anular compra</Boton></div>}
      </>}</Estado>
    </Ventana>
  );
}

export default function Compras() {
  const [filtro, setFiltro] = useState({ desde: '', hasta: hoy() });
  const [consulta, setConsulta] = useState(filtro);
  const [seleccion, setSeleccion] = useState(null);
  const [filas, recargar, error] = useCarga(() => api(`/api/compras?desde=${consulta.desde}&hasta=${consulta.hasta}`), [consulta]);
  const campo = k => e => setFiltro(f => ({ ...f, [k]: e.target.value }));
  return (
    <>
      <div className="encabezado"><h1>Compras registradas</h1>
        {sesion.puede('COMPRA_REGISTRAR') && <a className="boton" href="#/comprar">Nueva compra</a>}</div>
      <div className="panel fila">
        <Campo etiqueta="Desde (vacío = últimos 30 días)"><input type="date" name="desde" value={filtro.desde} onChange={campo('desde')} /></Campo>
        <Campo etiqueta="Hasta"><input type="date" name="hasta" value={filtro.hasta} onChange={campo('hasta')} /></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={() => setConsulta({ ...filtro })}>Buscar</Boton>
      </div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['documento', 'Ingreso', (v, f) => <a href="#" data-a="ver" onClick={e => { e.preventDefault(); setSeleccion(f.id); }}>{v}</a>],
          ['fecha_efectiva', 'Fecha', fecha], ['proveedor', 'Proveedor'], ['numero_factura_proveedor', 'Factura proveedor'],
          ['estado', 'Estado'], ['total', 'Total', dinero, 'num']]} />
      </Estado>
      {seleccion && <DetalleCompra key={seleccion} id={seleccion} alCerrar={() => setSeleccion(null)} alAnular={recargar} />}
    </>
  );
}
