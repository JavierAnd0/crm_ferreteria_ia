import { useState } from 'react';
import { faFloppyDisk, faMagnifyingGlass, faPlus } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { nombreTercero, siNo } from '../formato.js';
import { Boton, Campo, Estado, Tabla, Ventana, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

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
          <Campo etiqueta="Número (cédula o NIT)"><input name="numeroIdentificacion" defaultValue={t.numeroIdentificacion} required /></Campo>
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

export default function Terceros() {
  const [q, setQ] = useState('');
  const [consulta, setConsulta] = useState('');
  const [abierto, setAbierto] = useState(null);
  const [filas, , error] = useCarga(() => api(`/api/terceros?q=${encodeURIComponent(consulta)}`), [consulta]);
  const ver = f => e => { e.preventDefault(); setAbierto(f); };
  return (
    <>
      <div className="encabezado"><h1>Clientes y proveedores</h1>
        {sesion.puede('TERCERO_EDITAR') && <Boton data-a="nuevo" icono={faPlus} onClick={() => setAbierto({})}>Nuevo tercero</Boton>}</div>
      <div className="panel fila">
        <Campo etiqueta="Buscar"><input name="q" value={q} onChange={e => setQ(e.target.value)} onKeyDown={e => e.key === 'Enter' && setConsulta(q)} /></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={() => setConsulta(q)}>Buscar</Boton>
      </div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['tipoIdentificacion', 'Tipo'],
          ['numeroIdentificacion', 'Número', (v, f) => <a href="#" data-a="ver" onClick={ver(f)}>{v + (f.digitoVerificacion != null ? '-' + f.digitoVerificacion : '')}</a>],
          ['razonSocial', 'Nombre o razón social', (v, f) => <a href="#" onClick={ver(f)}>{nombreTercero(f)}</a>], ['telefono', 'Teléfono'], ['correo', 'Correo'],
          ['esCliente', 'Cliente', siNo], ['esProveedor', 'Proveedor', siNo]]} />
      </Estado>
      {abierto && <FormularioTercero key={abierto.id ?? 'nuevo'} t={abierto} alCerrar={() => setAbierto(null)}
        alGuardar={numero => { setAbierto(null); setQ(numero); setConsulta(numero); }} />}
    </>
  );
}
