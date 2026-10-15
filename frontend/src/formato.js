// Formatos de dinero, cantidades y fechas en español de Colombia.
export const dinero = v => v == null ? '' : Number(v).toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 2 });
export const num = v => v == null ? '' : Number(v).toLocaleString('es-CO', { maximumFractionDigits: 4 });
export const fecha = v => v ? new Date(v).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: String(v).length > 10 ? 'short' : undefined }) : '';
export const hoy = () => new Date().toLocaleDateString('sv-SE');
export const siNo = v => v ? 'Sí' : '';
export const nombreTercero = t => t ? (t.razonSocial || `${t.nombres} ${t.apellidos}`) : '';
