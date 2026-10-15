// Documento de ingreso: compra a un proveedor, con lote y vencimiento cuando el producto lo exige.
import { useState } from 'react';
import { faCheck, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { uuid } from '../offline.js';
import { dinero, hoy, nombreTercero, num } from '../formato.js';
import { Boton, Campo, Estado, Icono, Tabla, useAccion, useCarga, valores } from '../componentes.jsx';

export default function Comprar() {
  const accion = useAccion();
  const [proveedores, , error] = useCarga(() => api('/api/terceros?proveedores=true&limite=200'));
  const [lineas, setLineas] = useState([]);
  const [resultado, setResultado] = useState(null);

  const agregar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    const r = await api(`/api/referencias/codigo/${encodeURIComponent(v.codigo)}`);
    setLineas(ls => [...ls, { referenciaId: r.id, nombre: `${r.producto} ${r.marca} ${r.presentacion}`, unidad: v.unidad || null,
      cantidad: Number(v.cantidad), costoUnitario: Number(v.costo), lote: v.lote || null, fechaVencimiento: v.vence || null }]);
    e.target.reset();
  });

  const confirmar = accion(async () => {
    const c = valores(document.getElementById('encabezado-compra'));
    const r = await api('/api/compras', { metodo: 'POST', cuerpo: { uuid: uuid(), proveedorId: Number(c.proveedor),
      numeroFacturaProveedor: c.factura, fechaFacturaProveedor: c.ffactura, lineas: lineas.map(({ nombre, ...l }) => l) } });
    setResultado(<div className="panel ok"><Icono i={faCheck} /> Documento de ingreso {r.documento} por {dinero(r.total)}. Precios actualizados.</div>);
    setLineas([]);
  });

  return (
    <>
      <h1>Comprar</h1>
      <Estado datos={proveedores} error={error}>
        <form id="encabezado-compra" className="panel fila" onSubmit={e => e.preventDefault()}>
          <Campo etiqueta="Proveedor"><select name="proveedor">
            {proveedores?.map(p => <option key={p.id} value={p.id}>{nombreTercero(p)}</option>)}</select></Campo>
          <Campo etiqueta="N.º factura del proveedor"><input name="factura" /></Campo>
          <Campo etiqueta="Fecha factura"><input type="date" name="ffactura" defaultValue={hoy()} /></Campo>
        </form>
      </Estado>
      <div className="panel">
        <form className="fila" onSubmit={agregar}>
          <Campo etiqueta="Código de barras"><input name="codigo" size={18} required /></Campo>
          <Campo etiqueta="Unidad (vacío = base)"><input name="unidad" size={8} /></Campo>
          <Campo etiqueta="Cantidad"><input name="cantidad" type="number" step="any" min="0" style={{ width: 90 }} required /></Campo>
          <Campo etiqueta="Costo unitario sin IVA"><input name="costo" type="number" step="any" min="0" style={{ width: 120 }} required /></Campo>
          <Campo etiqueta="Lote"><input name="lote" size={10} /></Campo>
          <Campo etiqueta="Vence"><input name="vence" type="date" /></Campo>
          <Boton clase="secundario" data-a="agregar" icono={faPlus}>Agregar línea</Boton>
        </form>
        <div id="lineas">
          <Tabla filas={lineas} columnas={[['nombre', 'Referencia'], ['unidad', 'Unidad'], ['cantidad', 'Cantidad', num, 'num'],
            ['costoUnitario', 'Costo', dinero, 'num'], ['lote', 'Lote'], ['fechaVencimiento', 'Vence']]}
            acciones={(l, i) => <Boton clase="secundario" data-a="quitar" icono={faTrash} aria-label="Quitar"
              onClick={() => setLineas(ls => ls.filter((_, j) => j !== i))} />} />
        </div>
        <div className="fila"><Boton data-a="confirmar" icono={faCheck} disabled={!lineas.length} onClick={confirmar}>Confirmar compra</Boton></div>
      </div>
      <div id="resultado">{resultado}</div>
    </>
  );
}
