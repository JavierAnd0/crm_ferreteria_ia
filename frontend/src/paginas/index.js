// Rutas de la aplicación: #/nombre → componente. #/factura/{id} abre el detalle de una factura.
import Login from './Login.jsx';
import Inicio from './Inicio.jsx';
import Vender from './Vender.jsx';
import Ventas, { Factura } from './Ventas.jsx';
import Comprar from './Comprar.jsx';
import Productos from './Productos.jsx';
import Terceros from './Terceros.jsx';
import Reservas from './Reservas.jsx';
import Kardex from './Kardex.jsx';
import Cierres from './Cierres.jsx';
import Reportes from './Reportes.jsx';
import Migracion from './Migracion.jsx';
import Auditoria from './Auditoria.jsx';
import Usuarios from './Usuarios.jsx';
import Offline from './Offline.jsx';

export const PAGINAS = {
  login: Login, inicio: Inicio, vender: Vender, ventas: Ventas, comprar: Comprar, productos: Productos, terceros: Terceros,
  reservas: Reservas, kardex: Kardex, cierres: Cierres, reportes: Reportes, migracion: Migracion, auditoria: Auditoria,
  usuarios: Usuarios, offline: Offline,
};

/** Resuelve el componente de una ruta, incluida factura/{id}. */
export const paginaDe = ruta => (/^factura\/\d+$/.test(ruta) ? Factura : PAGINAS[ruta]);
