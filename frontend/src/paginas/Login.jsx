import { useEffect, useState } from 'react';
import { faRightToBracket } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { refrescarCatalogo } from '../offline.js';
import { Boton, MensajeError, valores } from '../componentes.jsx';

export default function Login() {
  const [error, setError] = useState(() => sesion.motivo());
  const [enviando, setEnviando] = useState(false);
  // Una sesión vencida que siga guardada no sirve: se descarta al llegar aquí.
  useEffect(() => { if (!sesion.vigente()) sesion.cerrar(); }, []);
  const ingresar = async e => {
    e.preventDefault();
    const { login, contrasena } = valores(e.target);
    sesion.olvidarMotivo(); setError(''); setEnviando(true);
    try {
      sesion.guardar(await api('/api/auth/login', { metodo: 'POST', cuerpo: { login, contrasena } }));
      refrescarCatalogo().catch(() => {});
      location.hash = '#/inicio';
    } catch (err) {
      setError(err.message);
      e.target.contrasena.select();
    } finally { setEnviando(false); }
  };
  return (
    <form className="panel login" onSubmit={ingresar} onInput={() => error && setError('')}>
      <h1>Ingresar</h1>
      <p><label>Usuario<br /><input name="login" autoComplete="username" required /></label></p>
      <p><label>Contraseña<br /><input name="contrasena" type="password" autoComplete="current-password" required /></label></p>
      <MensajeError>{error}</MensajeError>
      <Boton icono={faRightToBracket} disabled={enviando}>{enviando ? 'Ingresando…' : 'Ingresar'}</Boton>
    </form>
  );
}
