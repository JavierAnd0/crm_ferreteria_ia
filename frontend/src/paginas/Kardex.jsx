import { useState } from 'react';
import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { dinero, fecha, hoy, num } from '../formato.js';
import { Boton, Campo, Tabla, useAccion } from '../componentes.jsx';

export default function Kardex() {
  const accion = useAccion();
  const [filas, setFilas] = useState(null);
  const consultar = accion(async e => {
    const v = Object.fromEntries([...e.currentTarget.closest('.panel').querySelectorAll('input')].map(i => [i.name, i.value.trim()]));
    const ref = v.codigo ? (await api(`/api/referencias/codigo/${encodeURIComponent(v.codigo)}`)).id : '';
    setFilas(await api(`/api/kardex?referenciaId=${ref}&desde=${v.desde}&hasta=${v.hasta}`));
  });
  return (
    <>
      <h1>Kárdex</h1>
      <div className="panel fila">
        <Campo etiqueta="Código de barras (vacío = todos)"><input name="codigo" /></Campo>
        <Campo etiqueta="Desde"><input type="date" name="desde" /></Campo>
        <Campo etiqueta="Hasta"><input type="date" name="hasta" defaultValue={hoy()} /></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={consultar}>Consultar</Boton>
      </div>
      <div id="res">{filas && <Tabla filas={filas} columnas={[['fecha_efectiva', 'Fecha', fecha], ['tipo', 'Tipo'], ['documento', 'Documento'],
        ['referencia', 'Referencia'], ['codigo_lote', 'Lote'], ['estado_origen', 'De'], ['estado_destino', 'A'],
        ['cantidad_base', 'Cantidad', num, 'num'], ['saldo_total_despues', 'Saldo', num, 'num'],
        ['costo_promedio_despues', 'Costo prom.', dinero, 'num'], ['usuario', 'Usuario'], ['origen', 'Origen']]} />}</div>
    </>
  );
}
