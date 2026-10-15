import { useState } from 'react';
import { faFloppyDisk, faToggleOff, faToggleOn, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { siNo } from '../formato.js';
import { Boton, Campo, Estado, Tabla, Ventana, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

export default function Usuarios() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [roles] = useCarga(() => api('/api/usuarios/roles'));
  const [filas, recargar, error] = useCarga(() => api('/api/usuarios'));
  const [abierto, setAbierto] = useState(null);
  const nombreRol = c => roles?.find(r => r.codigo === c)?.nombre ?? c;

  const guardar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    const marcados = [...e.target.querySelectorAll('[name=rol]:checked')].map(x => x.value);
    if (!marcados.length) throw new Error('Elija al menos un rol');
    if (abierto.id) {
      await api(`/api/usuarios/${abierto.id}`, { metodo: 'PUT', cuerpo: { nombre: v.nombre, correo: v.correo || null, roles: marcados,
        contrasena: v.contrasena || null } });
      avisar('Usuario actualizado');
    } else {
      await api('/api/usuarios', { metodo: 'POST', cuerpo: { login: v.login, nombre: v.nombre, correo: v.correo || null, contrasena: v.contrasena, roles: marcados } });
      avisar('Usuario creado');
    }
    setAbierto(null); recargar();
  });
  const alternar = accion(async f => {
    await api(`/api/usuarios/${f.id}`, { metodo: 'PUT', cuerpo: { activo: !f.activo } }); recargar();
  });

  return (
    <>
      <div className="encabezado"><h1>Usuarios</h1>
        <Boton data-a="nuevo" icono={faUserPlus} onClick={() => setAbierto({ roles: [] })}>Nuevo usuario</Boton></div>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['login', 'Usuario', (v, f) => <a href="#" data-a="ver" onClick={e => { e.preventDefault(); setAbierto(f); }}>{v}</a>],
          ['nombre', 'Nombre'], ['roles', 'Roles', v => v.map(nombreRol).join(', ')], ['activo', 'Activo', siNo]]}
          acciones={f => <Boton clase="secundario" data-a="activo" icono={f.activo ? faToggleOff : faToggleOn} onClick={() => alternar(f)}>
            {f.activo ? 'Desactivar' : 'Activar'}</Boton>} />
      </Estado>
      {abierto && (
        <Ventana titulo={abierto.id ? `Usuario ${abierto.login}` : 'Nuevo usuario'} alCerrar={() => setAbierto(null)}>
          <form className="fila" id="usuario-form" onSubmit={guardar}>
            <Campo etiqueta="Usuario"><input name="login" defaultValue={abierto.login} readOnly={!!abierto.id} required /></Campo>
            <Campo etiqueta="Nombre"><input name="nombre" defaultValue={abierto.nombre} required /></Campo>
            <Campo etiqueta="Correo"><input name="correo" type="email" defaultValue={abierto.correo} /></Campo>
            <Campo etiqueta={abierto.id ? 'Nueva contraseña (vacío = no cambiar)' : 'Contraseña (mín. 8)'}>
              <input name="contrasena" type="password" minLength={8} required={!abierto.id} autoComplete="new-password" /></Campo>
            <fieldset className="roles"><legend>Roles</legend>
              {(roles || []).map(r => <label key={r.codigo}><span>
                <input type="checkbox" name="rol" value={r.codigo} defaultChecked={abierto.roles.includes(r.codigo)} /> {r.nombre}</span></label>)}
            </fieldset>
            <Boton icono={faFloppyDisk}>Guardar</Boton>
          </form>
        </Ventana>)}
    </>
  );
}
