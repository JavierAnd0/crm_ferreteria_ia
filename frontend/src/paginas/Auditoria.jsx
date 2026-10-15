import { useState } from 'react';
import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { fecha, hoy } from '../formato.js';
import { Boton, Campo, Tabla, useAccion } from '../componentes.jsx';

export default function Auditoria() {
  const accion = useAccion();
  const [filas, setFilas] = useState(null);
  const consultar = accion(async e => {
    const panel = e.currentTarget.closest('.panel');
    const q = new URLSearchParams(['desde', 'hasta', 'tabla', 'usuario', 'accion'].map(n => [n, panel.querySelector(`[name=${n}]`).value.trim()]));
    setFilas(await api(`/api/auditoria?${q}`));
  });
  return (
    <>
      <h1>Auditoría</h1>
      <div className="panel fila">
        <Campo etiqueta="Desde"><input type="date" name="desde" /></Campo>
        <Campo etiqueta="Hasta"><input type="date" name="hasta" defaultValue={hoy()} /></Campo>
        <Campo etiqueta="Tabla"><input name="tabla" size={14} /></Campo>
        <Campo etiqueta="Usuario"><input name="usuario" size={12} /></Campo>
        <Campo etiqueta="Acción"><select name="accion"><option value="">Todas</option><option>INSERT</option><option>UPDATE</option>
          <option>DELETE</option><option>CIERRE</option><option>REAPERTURA</option></select></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={consultar}>Consultar</Boton>
      </div>
      <div id="res">{filas && <Tabla filas={filas} columnas={[['ocurrido_en', 'Fecha', fecha], ['usuario', 'Usuario'], ['origen', 'Origen'],
        ['tabla', 'Tabla'], ['registro_id', 'Registro'], ['accion', 'Acción'],
        ['despues', 'Cambio', (v, f) => <details><summary>ver</summary><pre>{JSON.stringify({ antes: f.antes, despues: v }, null, 1)}</pre></details>]]} />}</div>
    </>
  );
}
