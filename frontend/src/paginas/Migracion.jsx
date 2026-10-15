import { useState } from 'react';
import { faCheckDouble, faFileArrowDown, faFileArrowUp } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { hoy } from '../formato.js';
import { Boton, Campo, Estado, Tabla, useAccion, useCarga } from '../componentes.jsx';

export default function Migracion() {
  const accion = useAccion();
  const [lista, recargar, error] = useCarga(() => api('/api/migracion'));
  const [detalle, setDetalle] = useState(null);
  const verDetalle = async id => setDetalle(await api(`/api/migracion/${id}`));

  const plantilla = accion(async () => {
    const r = await api('/api/migracion/plantilla', { crudo: true });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(await r.blob()); a.download = 'plantilla_migracion.xlsx'; a.click();
  });
  const subir = accion(async e => {
    e.preventDefault();
    const r = await api('/api/migracion', { metodo: 'POST', formulario: new FormData(e.target) });
    await verDetalle(r.id); recargar();
  });
  const cargar = accion(async id => {
    await api(`/api/migracion/${id}/cargar`, { metodo: 'POST' }); await verDetalle(id); recargar();
  });

  return (
    <>
      <h1>Migración desde Excel</h1>
      <div className="panel">
        <p>1. Descargue la plantilla, llene las hojas TERCEROS, PRODUCTOS y SALDOS.<br />2. Súbala: el sistema valida cada fila sin tocar el inventario.<br />3. Revise los errores y cargue las filas válidas.</p>
        <Boton clase="secundario" data-a="plantilla" icono={faFileArrowDown} onClick={plantilla}>Descargar plantilla</Boton>
      </div>
      <form className="panel fila" id="subir" onSubmit={subir}>
        <Campo etiqueta="Archivo .xlsx"><input type="file" name="archivo" accept=".xlsx" required /></Campo>
        <Campo etiqueta="Fecha de corte"><input type="date" name="fechaCorte" defaultValue={hoy()} required /></Campo>
        <Boton icono={faFileArrowUp}>Validar</Boton>
      </form>
      <div id="res">{detalle && <div className="panel">
        <strong>Importación {detalle.resumen.id}: {detalle.resumen.estado}</strong> · {detalle.resumen.total} filas, {detalle.resumen.validas} válidas,
        {' '}{detalle.resumen.errores} con error, {detalle.resumen.cargadas} cargadas
        <Tabla filas={detalle.filas.filter(f => f.estado === 'ERROR')}
          columnas={[['hoja', 'Hoja'], ['numero_fila', 'Fila'], ['errores', 'Errores', v => (v || []).join('; ')]]} />
      </div>}</div>
      <h2>Importaciones</h2>
      <Estado datos={lista} error={error}>
        <Tabla filas={lista} columnas={[['id', 'N.º'], ['archivo_nombre', 'Archivo'], ['fecha_corte', 'Corte'], ['estado', 'Estado'],
          ['filas_total', 'Filas', null, 'num'], ['filas_validas', 'Válidas', null, 'num'], ['filas_error', 'Errores', null, 'num']]}
          acciones={f => f.estado === 'VALIDADA' && <Boton data-a="cargar" icono={faCheckDouble} onClick={() => cargar(f.id)}>Cargar válidas</Boton>} />
      </Estado>
    </>
  );
}
