// Operación sin internet (RF-OFF-001): copia local del catálogo, bloque de facturas del equipo
// y cola de ventas pendientes. Cada venta lleva un UUID generado aquí: reenviarla no la duplica.
import { api } from './api.js';

const K = { catalogo: 'ferreteria.catalogo', cola: 'ferreteria.cola', equipo: 'ferreteria.dispositivo', bloque: 'ferreteria.bloque' };
const leer = (k, def) => { try { return JSON.parse(localStorage.getItem(k)) ?? def; } catch { return def; } };
const guardar = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* almacenamiento lleno o bloqueado */ } };

export const uuid = () => (crypto.randomUUID ? crypto.randomUUID()
  : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16); }));

export const local = {
  catalogo: () => leer(K.catalogo, []),
  equipo: () => leer(K.equipo, null),
  bloque: () => leer(K.bloque, null),
  cola: () => leer(K.cola, []),
  fijarEquipo(e) { guardar(K.equipo, e); },
  buscar(texto) {
    const t = texto.trim().toLowerCase();
    return this.catalogo().filter(r => r.codigoBarras === texto.trim()
      || `${r.producto} ${r.marca} ${r.presentacion}`.toLowerCase().includes(t)).slice(0, 20);
  },
};

export async function refrescarCatalogo() {
  guardar(K.catalogo, await api('/api/sync/catalogo'));
}

export async function pedirBloque(cantidad = 50) {
  const e = local.equipo();
  if (!e) throw new Error('Seleccione primero el equipo en «Sin internet»');
  const b = await api(`/api/sync/dispositivos/${e.id}/bloque?cantidad=${cantidad}`, { metodo: 'POST' });
  guardar(K.bloque, { prefijo: b.prefijo, siguiente: b.desde, hasta: b.hasta });
  return b;
}

/** Guarda la venta en la cola local y descuenta la copia local de existencias. */
export function venderSinConexion(venta) {
  const e = local.equipo(), b = local.bloque();
  if (!e || !b || b.siguiente > b.hasta) throw new Error('El equipo no tiene números de factura para trabajar sin internet');
  const catalogo = local.catalogo();
  for (const l of venta.lineas) {
    const r = catalogo.find(x => x.id === l.referenciaId);
    if (!r || Number(r.disponible) < l.cantidadBase) throw new Error(`Sin saldo local suficiente de ${r ? r.producto : l.referenciaId}`);
  }
  for (const l of venta.lineas) catalogo.find(x => x.id === l.referenciaId).disponible -= l.cantidadBase;
  const id = uuid();
  const op = { uuid: id, dispositivoId: e.id, prefijo: b.prefijo, numero: b.siguiente, creadoLocalEn: new Date().toISOString(),
    venta: { uuid: id, clienteId: venta.clienteId, formaPago: venta.formaPago,
      lineas: venta.lineas.map(({ cantidadBase, ...l }) => l) } };
  b.siguiente += 1;
  guardar(K.bloque, b); guardar(K.catalogo, catalogo); guardar(K.cola, [...local.cola(), op]);
  return `${op.prefijo}-${op.numero}`;
}

/** Envía la cola y quita toda venta que el servidor ya registró (aplicada, en conflicto o rechazada). */
export async function sincronizar() {
  const cola = local.cola();
  if (!cola.length) return [];
  const resultados = await api('/api/sync/ventas', { metodo: 'POST', cuerpo: cola });
  const procesadas = new Set(resultados.filter(r => ['APLICADA', 'CONFLICTO', 'RECHAZADA'].includes(r.estado)).map(r => r.uuid));
  guardar(K.cola, local.cola().filter(op => !procesadas.has(op.uuid)));
  return resultados;
}
