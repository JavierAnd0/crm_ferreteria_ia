// Códigos de barras: dibujo en SVG (EAN-13 si el código es válido, Code 128 para cualquier otro) e impresión de etiquetas.
import JsBarcode from 'jsbarcode';
import { dinero } from './formato.js';

/** Dígito de control GS1 para los 12 primeros dígitos de un EAN-13 (misma regla que CodigoBarras.java). */
export const digitoEan13 = doce => {
  const suma = [...doce].reduce((s, d, i) => s + Number(d) * (i % 2 ? 3 : 1), 0);
  return (10 - (suma % 10)) % 10;
};

export const esEan13 = codigo => /^\d{13}$/.test(codigo) && digitoEan13(codigo.slice(0, 12)) === Number(codigo[12]);

/** Formato con el que se dibuja el código: EAN-13 cuando cumple el estándar, Code 128 en cualquier otro caso. */
export const formatoDe = codigo => (esEan13(codigo) ? 'EAN13' : 'CODE128');

/** Dibuja el código dentro de un elemento <svg>; devuelve false si el código está vacío o no se puede dibujar. */
export function dibujarCodigo(svg, codigo, { alto = 50, ancho = 2, texto = true, fuente = 14 } = {}) {
  const valor = String(codigo ?? '').trim();
  if (!valor) return false;
  try {
    JsBarcode(svg, valor, { format: formatoDe(valor), height: alto, width: ancho, displayValue: texto, fontSize: fuente,
      margin: 4, background: '#ffffff', lineColor: '#000000', font: 'Inter, Arial, sans-serif' });
    return true;
  } catch {
    return false;
  }
}

/** SVG del código como texto, para la ventana de impresión (fuera de React); null si no se puede dibujar. */
export function svgCodigo(codigo, opciones) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  return dibujarCodigo(svg, codigo, opciones) ? svg.outerHTML : null;
}

const escapar = t => String(t ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

export const precioConIva = r => Math.round(Number(r.precioVenta) * (1 + Number(r.porcentajeIva || 0) / 100));

/** Tamaños de etiqueta: hoja carta (3 columnas) o rollo de impresora térmica de 50 × 25 mm. */
export const TAMANOS = {
  carta: { nombre: 'Hoja carta, 3 por fila (63 × 30 mm)', pagina: 'letter', ancho: '63mm', alto: '30mm', columnas: 3, margen: '10mm' },
  termica: { nombre: 'Rollo térmico 50 × 25 mm', pagina: '50mm 25mm', ancho: '50mm', alto: '25mm', columnas: 1, margen: '0' },
};

/** HTML de una etiqueta: nombre, presentación, código y, si se pide, precio con IVA. */
const htmlEtiqueta = (r, conPrecio) => `
  <div class="etiqueta-producto">
    <strong>${escapar(r.producto)}</strong>
    <span>${escapar([r.marca, r.presentacion].filter(Boolean).join(' · '))}</span>
    ${svgCodigo(r.codigoBarras, { alto: 34, ancho: 1.6, fuente: 12 }) ?? `<em>Código no válido: ${escapar(r.codigoBarras)}</em>`}
    ${conPrecio ? `<b>${escapar(dinero(precioConIva(r)))}</b>` : ''}
  </div>`;

/**
 * Abre una ventana con las etiquetas listas para imprimir. items = [{ referencia, cantidad }].
 * Se usa una ventana aparte para que la impresión no arrastre el menú ni la ventana emergente de la aplicación.
 */
export function imprimirEtiquetas(items, { conPrecio = true, tamano = 'carta' } = {}) {
  const t = TAMANOS[tamano];
  const etiquetas = items.flatMap(({ referencia, cantidad }) => Array.from({ length: cantidad }, () => htmlEtiqueta(referencia, conPrecio))).join('');
  const w = window.open('', '_blank', 'width=900,height=700');
  if (!w) throw new Error('El navegador bloqueó la ventana de impresión. Permita ventanas emergentes para este sitio.');
  w.document.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Etiquetas</title><style>
    @page { size: ${t.pagina}; margin: ${t.margen}; }
    body { margin: 0; font: 10px/1.2 Inter, Arial, sans-serif; color: #000; }
    .hoja { display: grid; grid-template-columns: repeat(${t.columnas}, ${t.ancho}); gap: ${t.columnas > 1 ? '2mm' : '0'}; }
    .etiqueta-producto { width: ${t.ancho}; height: ${t.alto}; overflow: hidden; display: flex; flex-direction: column; align-items: center;
      justify-content: center; text-align: center; padding: 1mm; box-sizing: border-box; break-inside: avoid; page-break-inside: avoid; }
    ${t.columnas > 1 ? '.etiqueta-producto { border: 1px dashed #ccc; }' : ''}
    .etiqueta-producto strong { font-size: 10px; max-height: 2.4em; overflow: hidden; }
    .etiqueta-producto span { font-size: 8px; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
    .etiqueta-producto svg { max-width: 100%; height: auto; max-height: 60%; }
    .etiqueta-producto b { font-size: 12px; }
  </style></head><body><div class="hoja">${etiquetas}</div>
  <script>window.onload = () => { window.focus(); window.print(); };<\/script></body></html>`);
  w.document.close();
}
