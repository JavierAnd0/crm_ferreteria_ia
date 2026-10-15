import { useState } from 'react';
import { faFloppyDisk, faMagnifyingGlass, faPlus } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { nombreTercero, siNo } from '../formato.js';
import { Boton, Campo, Estado, Icono, Tabla, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

export default function Terceros() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [q, setQ] = useState('');
  const [consulta, setConsulta] = useState('');
  const [filas, recargar, error] = useCarga(() => api(`/api/terceros?q=${encodeURIComponent(consulta)}`), [consulta]);
  const guardar = accion(async e => {
    e.preventDefault();
    const f = e.target, v = valores(f), o = k => v[k] || null;
    await api('/api/terceros', { metodo: 'POST', cuerpo: { tipoPersona: o('tipoPersona'), tipoIdentificacion: o('tipoIdentificacion'),
      numeroIdentificacion: o('numeroIdentificacion'), digitoVerificacion: v.digitoVerificacion ? Number(v.digitoVerificacion) : null,
      nombres: o('nombres'), apellidos: o('apellidos'), razonSocial: o('razonSocial'), direccion: o('direccion'), municipio: o('municipio'),
      telefono: o('telefono'), correo: o('correo'), esCliente: f.esCliente.checked, esProveedor: f.esProveedor.checked } });
    avisar('Tercero registrado'); f.reset(); recargar();
  });
  return (
    <>
      <h1>Clientes y proveedores</h1>
      <div className="panel fila">
        <Campo etiqueta="Buscar"><input name="q" value={q} onChange={e => setQ(e.target.value)} onKeyDown={e => e.key === 'Enter' && setConsulta(q)} /></Campo>
        <Boton data-a="buscar" icono={faMagnifyingGlass} onClick={() => setConsulta(q)}>Buscar</Boton>
      </div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['tipoIdentificacion', 'Tipo'],
          ['numeroIdentificacion', 'Número', (v, f) => v + (f.digitoVerificacion != null ? '-' + f.digitoVerificacion : '')],
          ['razonSocial', 'Nombre o razón social', (v, f) => nombreTercero(f)], ['telefono', 'Teléfono'], ['correo', 'Correo'],
          ['esCliente', 'Cliente', siNo], ['esProveedor', 'Proveedor', siNo]]} />
      </Estado>
      {sesion.puede('TERCERO_EDITAR') && (
        <details className="panel"><summary><strong><Icono i={faPlus} /> Nuevo tercero</strong></summary>
          <form className="fila" id="nuevo" onSubmit={guardar}>
            <Campo etiqueta="Tipo de persona"><select name="tipoPersona"><option>NATURAL</option><option>JURIDICA</option></select></Campo>
            <Campo etiqueta="Identificación"><select name="tipoIdentificacion"><option>CC</option><option>NIT</option><option>CE</option><option>PAS</option></select></Campo>
            <Campo etiqueta="Número (cédula o NIT)"><input name="numeroIdentificacion" required /></Campo>
            <Campo etiqueta="DV"><input name="digitoVerificacion" size={2} /></Campo>
            <Campo etiqueta="Nombre"><input name="nombres" /></Campo>
            <Campo etiqueta="Apellido"><input name="apellidos" /></Campo>
            <Campo etiqueta="Razón social"><input name="razonSocial" /></Campo>
            <Campo etiqueta="Dirección"><input name="direccion" /></Campo>
            <Campo etiqueta="Municipio"><input name="municipio" /></Campo>
            <Campo etiqueta="Teléfono"><input name="telefono" /></Campo>
            <Campo etiqueta="Correo"><input name="correo" type="email" /></Campo>
            <label><span><input type="checkbox" name="esCliente" defaultChecked /> Cliente</span></label>
            <label><span><input type="checkbox" name="esProveedor" /> Proveedor</span></label>
            <Boton icono={faFloppyDisk}>Guardar</Boton>
          </form>
        </details>)}
    </>
  );
}
