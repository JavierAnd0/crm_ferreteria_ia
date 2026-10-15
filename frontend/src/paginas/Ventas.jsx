import { useState } from 'react';
import { faArrowLeft, faBan, faMagnifyingGlass, faPrint } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { dinero, fecha, hoy, num } from '../formato.js';
import { Boton, Campo, Estado, Tabla, useAccion, useAvisar, useCarga } from '../componentes.jsx';

export default function Ventas() {
  const [filtro, setFiltro] = useState({ desde: '', hasta: hoy() });
  const [consulta, setConsulta] = useState(filtro);
  const [filas, , error] = useCarga(() => api(`/api/ventas?desde=${consulta.desde}&hasta=${consulta.hasta}`), [consulta]);
  const campo = k => e => setFiltro(f => ({ ...f, [k]: e.target.value }));
  return (
    <>
      <h1>Facturas</h1>
      <div className="panel fila">
        <Campo etiqueta="Desde"><input type="date" name="desde" value={filtro.desde} onChange={campo('desde')} /></Campo>
        <Campo etiqueta="Hasta"><input type="date" name="hasta" value={filtro.hasta} onChange={campo('hasta')} /></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={() => setConsulta({ ...filtro })}>Buscar</Boton>
      </div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['factura', 'Factura', (v, f) => <a href={`#/factura/${f.id}`}>{v}</a>],
          ['fecha_emision', 'Fecha', fecha], ['cliente', 'Cliente'], ['estado', 'Estado'], ['origen', 'Origen'], ['total', 'Total', dinero, 'num']]} />
      </Estado>
    </>
  );
}

export function Factura({ parametro: id }) {
  const accion = useAccion();
  const avisar = useAvisar();
  const [d, recargar, error] = useCarga(() => api(`/api/ventas/${id}`), [id]);
  const anular = accion(async () => {
    const motivo = prompt('Motivo de la anulación');
    if (!motivo) return;
    await api(`/api/ventas/${id}/anular`, { metodo: 'POST', cuerpo: { motivo } });
    avisar('Factura anulada; las existencias volvieron a disponible');
    await recargar();
  });
  return (
    <Estado datos={d} error={error}>{d && (() => {
      const { factura: f, lineas } = d;
      return <>
        <h1>Factura {f.factura} <span className="suave">{f.estado}</span></h1>
        <div className="panel">
          <p><strong>Cliente:</strong> {f.cliente} · {f.identificacion_cliente}<br />
            <strong>Fecha:</strong> {fecha(f.fecha_emision)} · <strong>Forma de pago:</strong> {f.forma_pago} · <strong>Vendedor:</strong> {f.vendedor}</p>
          <Tabla filas={lineas} columnas={[['referencia', 'Referencia'], ['unidad', 'Unidad'], ['cantidad', 'Cantidad', num, 'num'],
            ['precio_unitario', 'Precio', dinero, 'num'], ['valor_descuento', 'Descuento', dinero, 'num'], ['total_linea', 'Total', dinero, 'num'],
            ['codigo_lote', 'Lote'], ['fecha_fin_garantia', 'Garantía hasta', fecha]]} />
          <p className="total">Subtotal {dinero(f.subtotal)} · Descuento {dinero(f.valor_descuento)} · IVA {dinero(f.valor_impuestos)} · Total {dinero(f.total)}</p>
          <div className="fila">
            <Boton clase="secundario" icono={faArrowLeft} onClick={() => { location.hash = '#/ventas'; }}>Volver a facturas</Boton>
            <Boton icono={faPrint} onClick={() => print()}>Imprimir</Boton>
            {sesion.puede('VENTA_ANULAR') && f.estado === 'EMITIDA' && <Boton clase="peligro" data-a="anular" icono={faBan} onClick={anular}>Anular</Boton>}
          </div>
        </div>
      </>;
    })()}</Estado>
  );
}
