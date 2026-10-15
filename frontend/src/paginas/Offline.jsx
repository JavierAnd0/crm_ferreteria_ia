// Preparar el equipo para vender sin internet: equipo, bloque de numeración, catálogo local, cola y conflictos.
import { useState } from 'react';
import { faArrowsRotate, faDatabase, faHashtag, faHandshake } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { local, pedirBloque, refrescarCatalogo, sincronizar } from '../offline.js';
import { fecha } from '../formato.js';
import { Boton, Campo, Tabla, useAccion, useAvisar, useCarga } from '../componentes.jsx';

export default function Offline() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [, setVersion] = useState(0);
  const repintar = () => setVersion(v => v + 1);
  const [equipos] = useCarga(() => api('/api/sync/dispositivos').catch(() => []));
  const revisaConflictos = sesion.puede('PRODUCTO_EDITAR', 'VENTA_ANULAR');
  const [conflictos, recargarConflictos] = useCarga(() => revisaConflictos ? api('/api/sync/conflictos').catch(() => []) : null);

  const equipo = local.equipo(), b = local.bloque();
  const elegir = e => {
    const x = (equipos || []).find(q => String(q.id) === e.target.value);
    local.fijarEquipo(x ? { id: x.id, nombre: x.nombre } : null); repintar();
  };
  const bloque = accion(async () => { const r = await pedirBloque(50); avisar(`Números ${r.prefijo} ${r.desde} a ${r.hasta} asignados`); repintar(); });
  const catalogo = accion(async () => { await refrescarCatalogo(); avisar('Catálogo local actualizado'); repintar(); });
  const sync = accion(async () => {
    const r = await sincronizar(); avisar(`${r.length} operación(es) procesadas`);
    window.dispatchEvent(new Event('cola-cambio')); repintar(); recargarConflictos();
  });
  const resolver = accion(async f => {
    const resolucion = prompt('¿Cómo se resolvió? (por ejemplo: se anuló la factura en papel y se devolvió el dinero)');
    if (!resolucion) return;
    await api(`/api/sync/conflictos/${f.uuid}/resolver`, { metodo: 'POST', cuerpo: { resolucion } });
    avisar('Conflicto resuelto'); recargarConflictos();
  });

  return (
    <>
      <h1>Trabajar sin internet</h1>
      <div className="panel">
        <p>Este equipo guarda una copia del catálogo y un bloque de números de factura. Sin conexión, la venta se guarda aquí con un identificador único y se envía sola al volver internet. Si otra caja vendió la misma existencia, la venta queda en conflicto para revisión: nunca se generan saldos negativos.</p>
        <div className="fila">
          <Campo etiqueta="Equipo"><select name="equipo" value={equipo?.id ?? ''} onChange={elegir}><option value="">—</option>
            {(equipos || []).filter(x => x.permite_fuera_linea).map(x => <option key={x.id} value={x.id}>{x.nombre}</option>)}</select></Campo>
          <Boton data-a="bloque" icono={faHashtag} onClick={bloque}>Pedir 50 números</Boton>
          <Boton clase="secundario" data-a="catalogo" icono={faDatabase} onClick={catalogo}>Actualizar catálogo local</Boton>
          <Boton clase="secundario" data-a="sync" icono={faArrowsRotate} onClick={sync}>Sincronizar ahora</Boton>
        </div>
        <p>Bloque actual: {b ? `${b.prefijo} ${b.siguiente} a ${b.hasta} (${b.hasta - b.siguiente + 1} disponibles)` : 'ninguno'} · Catálogo local: {local.catalogo().length} referencias</p>
      </div>
      <h2>Ventas pendientes en este equipo</h2>
      <Tabla filas={local.cola()} columnas={[['prefijo', 'Factura', (v, f) => `${v}-${f.numero}`], ['creadoLocalEn', 'Fecha', fecha], ['uuid', 'Identificador']]} />
      {revisaConflictos && conflictos && <div id="conflictos"><h2>Conflictos por resolver</h2>
        <Tabla filas={conflictos} columnas={[['dispositivo', 'Equipo'], ['usuario', 'Usuario'], ['creado_local_en', 'Fecha local', fecha], ['detalle_conflicto', 'Motivo']]}
          acciones={f => <Boton clase="secundario" data-a="resolver" icono={faHandshake} onClick={() => resolver(f)}>Resolver</Boton>} /></div>}
    </>
  );
}
