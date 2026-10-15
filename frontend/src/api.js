// Cliente de la API REST: agrega el token JWT y el equipo, y traduce errores RFC 7807.
const CLAVE_SESION = 'ferreteria.sesion';

export const sesion = {
  leer() { try { return JSON.parse(localStorage.getItem(CLAVE_SESION)); } catch { return null; } },
  guardar(s) { try { localStorage.setItem(CLAVE_SESION, JSON.stringify(s)); } catch { /* sin almacenamiento */ } },
  cerrar() { try { localStorage.removeItem(CLAVE_SESION); } catch { /* sin almacenamiento */ } },
  vigente() { const s = this.leer(); return s && new Date(s.expira) > new Date() ? s : null; },
  puede(...permisos) { const s = this.leer(); return !!s && permisos.some(p => s.permisos.includes(p)); },
};

export class ErrorApi extends Error {
  constructor(estado, mensaje, sinRed = false) { super(mensaje); this.estado = estado; this.sinRed = sinRed; }
}

export async function api(ruta, { metodo = 'GET', cuerpo, formulario, crudo = false } = {}) {
  const s = sesion.leer();
  const cabeceras = {};
  if (s?.token) cabeceras.Authorization = `Bearer ${s.token}`;
  const equipo = localStorage.getItem('ferreteria.dispositivo');
  if (equipo) cabeceras['X-Dispositivo-Id'] = JSON.parse(equipo).id;
  let body;
  if (formulario) body = formulario;
  else if (cuerpo !== undefined) { cabeceras['Content-Type'] = 'application/json'; body = JSON.stringify(cuerpo); }
  let r;
  try {
    r = await fetch(ruta, { method: metodo, headers: cabeceras, body });
  } catch {
    throw new ErrorApi(0, 'Sin conexión con el servidor', true);
  }
  if (r.status === 401 && !ruta.endsWith('/login')) { sesion.cerrar(); location.hash = '#/login'; throw new ErrorApi(401, 'La sesión terminó'); }
  if (!r.ok) {
    let detalle = r.statusText;
    try { const p = await r.json(); detalle = p.detail || p.message || detalle; } catch { /* sin cuerpo */ }
    throw new ErrorApi(r.status, detalle);
  }
  if (crudo) return r;
  return r.status === 204 || r.headers.get('content-length') === '0' ? null : r.json().catch(() => null);
}
