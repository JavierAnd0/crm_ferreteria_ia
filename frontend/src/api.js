// Cliente de la API REST: agrega el token JWT y el equipo, y traduce errores RFC 7807.
const CLAVE_SESION = 'ferreteria.sesion';
const CLAVE_MOTIVO = 'ferreteria.motivo-salida';

export const sesion = {
  leer() { try { return JSON.parse(localStorage.getItem(CLAVE_SESION)); } catch { return null; } },
  guardar(s) { try { localStorage.setItem(CLAVE_SESION, JSON.stringify(s)); } catch { /* sin almacenamiento */ } },
  cerrar() { try { localStorage.removeItem(CLAVE_SESION); } catch { /* sin almacenamiento */ } },
  /** Cierra la sesión que el servidor rechazó y deja el motivo para mostrarlo en la pantalla de ingreso. */
  terminar(motivo) {
    this.cerrar();
    try { sessionStorage.setItem(CLAVE_MOTIVO, motivo); } catch { /* sin almacenamiento */ }
    location.hash = '#/login';
  },
  motivo() { try { return sessionStorage.getItem(CLAVE_MOTIVO) || ''; } catch { return ''; } },
  olvidarMotivo() { try { sessionStorage.removeItem(CLAVE_MOTIVO); } catch { /* sin almacenamiento */ } },
  vigente() { const s = this.leer(); return s && new Date(s.expira) > new Date() ? s : null; },
  puede(...permisos) { const s = this.leer(); return !!s && permisos.some(p => s.permisos.includes(p)); },
};

export class ErrorApi extends Error {
  constructor(estado, mensaje, sinRed = false) { super(mensaje); this.estado = estado; this.sinRed = sinRed; }
}

// Mensaje para cuando el servidor no explica el error (cuerpo vacío o una página de un proxy).
const MENSAJE_POR_ESTADO = {
  400: 'Revise los datos: hay campos con valores no válidos.',
  401: 'La sesión terminó o no es válida. Ingrese de nuevo.',
  403: 'No tiene permiso para esta operación.',
  404: 'No se encontró lo que busca.',
  409: 'El registro choca con otro que ya existe.',
  422: 'La operación no cumple una regla del negocio.',
  502: 'El servidor está arrancando. Espere un minuto e intente de nuevo.',
  503: 'El servidor está arrancando. Espere un minuto e intente de nuevo.',
  504: 'El servidor tardó demasiado en responder. Intente de nuevo.',
};
export const SIN_CONEXION = 'No hay conexión con el servidor. Revise internet e intente de nuevo.';

export const mensajeDeEstado = estado =>
  MENSAJE_POR_ESTADO[estado] || (estado >= 500 ? 'El servidor tuvo un problema. Intente de nuevo en un momento.' : `Error ${estado}`);

const esIngreso = ruta => ruta.endsWith('/api/auth/login');

export async function api(ruta, { metodo = 'GET', cuerpo, formulario, crudo = false } = {}) {
  const s = sesion.leer();
  const cabeceras = {};
  // Al ingresar no se envía el token anterior: si venció, el servidor lo rechazaría antes de revisar la clave.
  if (s?.token && !esIngreso(ruta)) cabeceras.Authorization = `Bearer ${s.token}`;
  const equipo = localStorage.getItem('ferreteria.dispositivo');
  if (equipo) cabeceras['X-Dispositivo-Id'] = JSON.parse(equipo).id;
  let body;
  if (formulario) body = formulario;
  else if (cuerpo !== undefined) { cabeceras['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
  let r;
  try {
    r = await fetch(ruta, { method: metodo, headers: cabeceras, body });
  } catch {
    throw new ErrorApi(0, SIN_CONEXION, true);
  }
  if (!r.ok) {
    let detalle = '';
    try { const p = await r.json(); detalle = p.detail || p.message || ''; } catch { /* sin cuerpo o no es JSON */ }
    const mensaje = detalle.trim() || mensajeDeEstado(r.status);
    if (r.status === 401 && !esIngreso(ruta)) sesion.terminar(mensaje);
    throw new ErrorApi(r.status, mensaje);
  }
  if (crudo) return r;
  return r.status === 204 || r.headers.get('content-length') === '0' ? null : r.json().catch(() => null);
}
