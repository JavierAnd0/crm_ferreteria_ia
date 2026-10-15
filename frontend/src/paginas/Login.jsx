import { faRightToBracket } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { refrescarCatalogo } from '../offline.js';
import { Boton, useAccion, valores } from '../componentes.jsx';

export default function Login() {
  const accion = useAccion();
  const ingresar = accion(async e => {
    e.preventDefault();
    const { login, contrasena } = valores(e.target);
    sesion.guardar(await api('/api/auth/login', { metodo: 'POST', cuerpo: { login, contrasena } }));
    refrescarCatalogo().catch(() => {});
    location.hash = '#/inicio';
  });
  return (
    <form className="panel login" onSubmit={ingresar}>
      <h1>Ingresar</h1>
      <p><label>Usuario<br /><input name="login" autoComplete="username" required /></label></p>
      <p><label>Contraseña<br /><input name="contrasena" type="password" autoComplete="current-password" required /></label></p>
      <Boton icono={faRightToBracket}>Ingresar</Boton>
    </form>
  );
}
