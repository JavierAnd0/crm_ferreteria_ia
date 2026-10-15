import { useState } from 'react';
import { faLock, faLockOpen, faScaleBalanced } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { dinero, fecha, num } from '../formato.js';
import { Boton, Estado, Tabla, useAccion, useAvisar, useCarga } from '../componentes.jsx';

export default function Cierres() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [filas, recargar, error] = useCarga(() => api('/api/periodos'));
  const [saldos, setSaldos] = useState(null);

  const cerrar = accion(async id => {
    const r = await api(`/api/periodos/${id}/cerrar`, { metodo: 'POST' });
    avisar(`Período ${id} cerrado (${r.saldos} saldos)`); recargar();
  });
  const reabrir = accion(async id => {
    const motivo = prompt('Motivo de la reapertura');
    if (!motivo) return;
    await api(`/api/periodos/${id}/reabrir`, { metodo: 'POST', cuerpo: { motivo } }); recargar();
  });
  const verSaldos = accion(async id => setSaldos({ id, filas: await api(`/api/periodos/${id}/saldos`) }));

  return (
    <>
      <h1>Cierres mensuales</h1>
      <p className="suave">El cierre se ejecuta solo el día 1 de cada mes a las 02:00 (hora de Colombia). Un mes cerrado no admite movimientos.</p>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['id', 'Período'], ['estado', 'Estado'], ['cierre_programado', 'Cierre programado', fecha],
          ['cerrado_en', 'Cerrado', fecha], ['cerrado_por', 'Por'], ['motivo_reapertura', 'Motivo de reapertura']]}
          acciones={f => <>
            {sesion.puede('CIERRE_EJECUTAR') && f.estado !== 'CERRADO' &&
              <Boton clase="secundario" data-a="cerrar" icono={faLock} onClick={() => cerrar(f.id)}>Cerrar</Boton>}
            {sesion.puede('CIERRE_REABRIR') && f.estado === 'CERRADO' &&
              <Boton clase="secundario" data-a="reabrir" icono={faLockOpen} onClick={() => reabrir(f.id)}>Reabrir</Boton>}
            {sesion.puede('COSTO_VER', 'REPORTE_CONSULTAR') && f.estado === 'CERRADO' &&
              <Boton clase="secundario" data-a="saldos" icono={faScaleBalanced} onClick={() => verSaldos(f.id)}>Saldos</Boton>}
          </>} />
      </Estado>
      {saldos && <div id="saldos"><h2>Saldos al cierre {saldos.id}</h2>
        <Tabla filas={saldos.filas} columnas={[['referencia', 'Referencia'], ['codigo_lote', 'Lote'], ['estado', 'Estado'],
          ['cantidad', 'Cantidad', num, 'num'], ['costo_promedio', 'Costo', dinero, 'num'], ['valor_total', 'Valor', dinero, 'num']]} /></div>}
    </>
  );
}
