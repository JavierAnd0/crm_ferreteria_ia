import { useState } from 'react';
import { faFloppyDisk, faMagnifyingGlass, faPlus } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { nombreTercero } from '../formato.js';
import { Avatar, Boton, Buscador, Campo, Estado, Etiqueta, Tabla, Ventana, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

/** Formulario de tercero en ventana: crea (sin t.id) o actualiza (con t.id). */
function FormularioTercero({ t, alGuardar, alCerrar }) {
  const accion = useAccion();
  const avisar = useAvisar();
  const editable = sesion.puede('TERCERO_EDITAR');
  const guardar = accion(async e => {
    e.preventDefault();
    const f = e.target, v = valores(f), o = k => v[k] || null;
    const cuerpo = { tipoPersona: o('tipoPersona'), tipoIdentificacion: o('tipoIdentificacion'),
      numeroIdentificacion: o('numeroIdentificacion'), digitoVerificacion: v.digitoVerificacion ? Number(v.digitoVerificacion) : null,
      nombres: o('nombres'), apellidos: o('apellidos'), razonSocial: o('razonSocial'), direccion: o('direccion'), municipio: o('municipio'),
      telefono: o('telefono'), correo: o('correo'), esCliente: f.esCliente.checked, esProveedor: f.esProveedor.checked };
    await api(t.id ? `/api/terceros/${t.id}` : '/api/terceros', { metodo: t.id ? 'PUT' : 'POST', cuerpo });
    avisar(t.id ? 'Tercero actualizado' : 'Tercero registrado'); alGuardar(v.numeroIdentificacion);
  });
  return (
    <Ventana titulo={t.id ? nombreTercero(t) : 'Nuevo cliente o proveedor'} alCerrar={alCerrar}>
      <form className="fila" id="tercero" onSubmit={guardar}>
        <fieldset disabled={!editable} className="fila sin-borde">
          <Campo etiqueta="Tipo de persona"><select name="tipoPersona" defaultValue={t.tipoPersona}><option>NATURAL</option><option>JURIDICA</option></select></Campo>
          <Campo etiqueta="Identificación"><select name="tipoIdentificacion" defaultValue={t.tipoIdentificacion}>
            <option>CC</option><option>NIT</option><option>CE</option><option>PAS</option></select></Campo>
          <Campo etiqueta="Número (cédula o NIT)"><input name="numeroIdentificacion" defaultValue={t.numeroIdentificacion} readOnly={!!t.id} required /></Campo>
          <Campo etiqueta="DV"><input name="digitoVerificacion" size={2} defaultValue={t.digitoVerificacion ?? ''} /></Campo>
          <Campo etiqueta="Nombre"><input name="nombres" defaultValue={t.nombres} /></Campo>
          <Campo etiqueta="Apellido"><input name="apellidos" defaultValue={t.apellidos} /></Campo>
          <Campo etiqueta="Razón social"><input name="razonSocial" defaultValue={t.razonSocial} /></Campo>
          <Campo etiqueta="Dirección"><input name="direccion" defaultValue={t.direccion} /></Campo>
          <Campo etiqueta="Municipio"><input name="municipio" defaultValue={t.municipio} /></Campo>
          <Campo etiqueta="Teléfono"><input name="telefono" defaultValue={t.telefono} /></Campo>
          <Campo etiqueta="Correo"><input name="correo" type="email" defaultValue={t.correo} /></Campo>
          <label><span><input type="checkbox" name="esCliente" defaultChecked={t.esCliente ?? true} /> Cliente</span></label>
          <label><span><input type="checkbox" name="esProveedor" defaultChecked={t.esProveedor} /> Proveedor</span></label>
          {editable && <Boton icono={faFloppyDisk}>Guardar</Boton>}
        </fieldset>
      </form>
    </Ventana>
  );
}

const FILTROS = [['', 'Todos'], ['clientes', 'Clientes'], ['proveedores', 'Proveedores']];
// Color estable por tercero para que el mismo cliente se reconozca siempre igual.
const COLORES = ['var(--primario)', 'var(--ok)', 'var(--info)', 'var(--aviso)', 'var(--error)', 'var(--secundario)'];
const colorDe = t => COLORES[Number(String(t.numeroIdentificacion).replace(/\D/g, '').slice(-3) || 0) % COLORES.length];
const iniciales = t => (t.razonSocial || `${t.nombres || ''} ${t.apellidos || ''}`).split(/\s+/)
  .filter(p => p.length > 2 || /^[A-ZÁÉÍÓÚÑ]/.test(p)).slice(0, 2).map(p => p[0].toUpperCase()).join('');
const identificacion = t => t.numeroIdentificacion + (t.digitoVerificacion != null ? '-' + t.digitoVerificacion : '');

export default function Terceros() {
  const [q, setQ] = useState('');
  const [consulta, setConsulta] = useState({ q: '', filtro: '' });
  const [abierto, setAbierto] = useState(null);
  const [filas, , error] = useCarga(() =>
    api(`/api/terceros?q=${encodeURIComponent(consulta.q)}${consulta.filtro ? `&${consulta.filtro}=true` : ''}`), [consulta]);
  const ver = f => e => { e.preventDefault(); setAbierto(f); };
  return (
    <>
      <div className="encabezado"><h1>Clientes y proveedores</h1>
        {sesion.puede('TERCERO_EDITAR') && <Boton data-a="nuevo" icono={faPlus} onClick={() => setAbierto({})}>Nuevo tercero</Boton>}</div>
      <div className="panel">
        <div className="cabeza-panel fila">
          <Buscador name="q" placeholder="Buscar por nombre, cédula o NIT" valor={q} alCambiar={setQ} alBuscar={() => setConsulta(c => ({ ...c, q }))} />
          <Boton data-a="buscar" clase="secundario" icono={faMagnifyingGlass} onClick={() => setConsulta(c => ({ ...c, q }))}>Buscar</Boton>
          <span className="espacio" />
          <select name="filtro" aria-label="Mostrar" value={consulta.filtro} onChange={e => setConsulta(c => ({ ...c, filtro: e.target.value }))}>
            {FILTROS.map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select>
        </div>
        <Estado datos={filas} error={error}>
          <Tabla filas={filas} alElegir={setAbierto} vacio="No hay terceros que coincidan con la búsqueda." columnas={[
            ['razonSocial', 'Tercero', (v, f) => (
              <span className="celda-principal"><Avatar color={colorDe(f)}>{iniciales(f) || '?'}</Avatar>
                <span><a href="#" className="enlace-fila nombre" onClick={ver(f)}>{nombreTercero(f)}</a>
                  <small>{f.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural'}{f.municipio ? ` · ${f.municipio}` : ''}</small></span></span>)],
            ['numeroIdentificacion', 'Identificación', (v, f) => (
              <span><Etiqueta tipo="gris">{f.tipoIdentificacion}</Etiqueta> <a href="#" data-a="ver" className="enlace-fila mono" onClick={ver(f)}>{identificacion(f)}</a></span>)],
            ['telefono', 'Contacto', (v, f) => <span>{v || <span className="suave">—</span>}{f.correo && <small className="suave" style={{ display: 'block' }}>{f.correo}</small>}</span>],
            ['esCliente', 'Rol', (v, f) => <span className="fila" style={{ gap: '.375rem' }}>
              {f.esCliente && <Etiqueta tipo="info">Cliente</Etiqueta>}{f.esProveedor && <Etiqueta tipo="aviso">Proveedor</Etiqueta>}</span>]]} />
        </Estado>
      </div>
      {abierto && <FormularioTercero key={abierto.id ?? 'nuevo'} t={abierto} alCerrar={() => setAbierto(null)}
        alGuardar={numero => { setAbierto(null); setQ(numero); setConsulta({ q: numero, filtro: '' }); }} />}
    </>
  );
}
