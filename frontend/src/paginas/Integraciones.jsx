// Sistemas externos (facturación electrónica, contabilidad...) y eventos enviados, con reintento de los fallidos.
import { useState } from 'react';
import { faFloppyDisk, faPlus, faRotateRight } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { fecha, siNo } from '../formato.js';
import { Boton, Campo, Estado, Tabla, Ventana, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

export default function Integraciones() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [sistemas, recargarSistemas, errorSistemas] = useCarga(() => api('/api/integracion/sistemas'));
  const [estado, setEstado] = useState('');
  const [eventos, recargarEventos, errorEventos] = useCarga(() => api(`/api/integracion/eventos?estado=${estado}`), [estado]);
  const [editando, setEditando] = useState(null);

  const guardar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    await api('/api/integracion/sistemas', { metodo: 'POST', cuerpo: { codigo: v.codigo, nombre: v.nombre, tipo: v.tipo,
      endpointUrl: v.endpointUrl || null, activo: e.target.activo.checked } });
    avisar('Sistema guardado'); setEditando(null); recargarSistemas();
  });
  const reintentar = accion(async id => {
    await api(`/api/integracion/eventos/${id}/reintentar`, { metodo: 'POST' }); avisar('Evento puesto en cola de nuevo'); recargarEventos();
  });

  return (
    <>
      <div className="encabezado"><h1>Integraciones</h1>
        <Boton data-a="nuevo" icono={faPlus} onClick={() => setEditando({ activo: true, tipo: 'OTRO' })}>Nuevo sistema</Boton></div>
      <Estado datos={sistemas} error={errorSistemas}>
        <Tabla filas={sistemas} columnas={[['codigo', 'Código', (v, f) => <a href="#" onClick={e => { e.preventDefault(); setEditando(f); }}>{v}</a>],
          ['nombre', 'Nombre'], ['tipo', 'Tipo'], ['endpoint_url', 'URL'], ['activo', 'Activo', siNo]]} />
      </Estado>
      <div className="encabezado"><h2>Eventos enviados</h2>
        <Campo etiqueta="Estado"><select name="estado" value={estado} onChange={e => setEstado(e.target.value)}>
          <option value="">Todos</option><option>PENDIENTE</option><option>ENVIADO</option><option>ERROR</option><option>DESCARTADO</option></select></Campo></div>
      <Estado datos={eventos} error={errorEventos}>
        <Tabla filas={eventos} columnas={[['creado_en', 'Fecha', fecha], ['sistema', 'Sistema'], ['tipo_evento', 'Evento'],
          ['entidad', 'Entidad'], ['entidad_id', 'Id'], ['estado', 'Estado'], ['intentos', 'Intentos', null, 'num'], ['ultimo_error', 'Último error']]}
          acciones={f => f.estado === 'ERROR' && <Boton clase="secundario" data-a="reintentar" icono={faRotateRight} onClick={() => reintentar(f.id)}>Reintentar</Boton>} />
      </Estado>
      {editando && (
        <Ventana titulo={editando.codigo ? `Sistema ${editando.codigo}` : 'Nuevo sistema'} alCerrar={() => setEditando(null)}>
          <form className="fila" id="sistema" onSubmit={guardar}>
            <Campo etiqueta="Código"><input name="codigo" defaultValue={editando.codigo} readOnly={!!editando.codigo} required /></Campo>
            <Campo etiqueta="Nombre"><input name="nombre" defaultValue={editando.nombre} required /></Campo>
            <Campo etiqueta="Tipo"><select name="tipo" defaultValue={editando.tipo}>
              <option>FACTURACION_ELECTRONICA</option><option>CONTABILIDAD</option><option>COMERCIO_ELECTRONICO</option><option>OTRO</option></select></Campo>
            <Campo etiqueta="URL de envío"><input name="endpointUrl" type="url" size={36} defaultValue={editando.endpoint_url} /></Campo>
            <label><span><input type="checkbox" name="activo" defaultChecked={editando.activo} /> Activo</span></label>
            <Boton icono={faFloppyDisk}>Guardar</Boton>
          </form>
        </Ventana>)}
    </>
  );
}
