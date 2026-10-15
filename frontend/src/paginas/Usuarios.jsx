import { faToggleOff, faToggleOn, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { api } from '../api.js';
import { siNo } from '../formato.js';
import { Boton, Campo, Estado, Tabla, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

export default function Usuarios() {
  const accion = useAccion();
  const avisar = useAvisar();
  const [roles] = useCarga(() => api('/api/usuarios/roles'));
  const [filas, recargar, error] = useCarga(() => api('/api/usuarios'));
  const crear = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    await api('/api/usuarios', { metodo: 'POST', cuerpo: { login: v.login, nombre: v.nombre, contrasena: v.contrasena, roles: [v.rol] } });
    avisar('Usuario creado'); e.target.reset(); recargar();
  });
  const alternar = accion(async f => {
    await api(`/api/usuarios/${f.id}`, { metodo: 'PUT', cuerpo: { activo: !f.activo } }); recargar();
  });
  return (
    <>
      <h1>Usuarios</h1>
      <form className="panel fila" id="nuevo" onSubmit={crear}>
        <Campo etiqueta="Usuario"><input name="login" required /></Campo>
        <Campo etiqueta="Nombre"><input name="nombre" required /></Campo>
        <Campo etiqueta="Contraseña (mín. 8)"><input name="contrasena" type="password" minLength={8} required /></Campo>
        <Campo etiqueta="Rol"><select name="rol">{(roles || []).map(r => <option key={r.codigo} value={r.codigo}>{r.nombre}</option>)}</select></Campo>
        <Boton icono={faUserPlus}>Crear</Boton>
      </form>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['login', 'Usuario'], ['nombre', 'Nombre'], ['roles', 'Roles', v => v.join(', ')], ['activo', 'Activo', siNo]]}
          acciones={f => <Boton clase="secundario" data-a="activo" icono={f.activo ? faToggleOff : faToggleOn} onClick={() => alternar(f)}>
            {f.activo ? 'Desactivar' : 'Activar'}</Boton>} />
      </Estado>
    </>
  );
}
