// Arranque de la interfaz: sesión, menú según permisos, enrutador por hash y estado de conexión.
import { useCallback, useEffect, useState } from 'react';
import {
  faAddressBook, faBars, faBoxesStacked, faCalendarCheck, faCashRegister, faChartLine, faFileExcel,
  faFileInvoiceDollar, faHouse, faListOl, faRightFromBracket, faShieldHalved, faTriangleExclamation,
  faTruckRampBox, faUsers, faWifi,
} from '@fortawesome/free-solid-svg-icons';
import { sesion } from './api.js';
import { local, refrescarCatalogo, sincronizar } from './offline.js';
import { Boton, Icono, useAvisar } from './componentes.jsx';
import { PAGINAS, paginaDe } from './paginas/index.js';

const MENU = [
  ['inicio', 'Inicio', faHouse, []],
  ['vender', 'Vender', faCashRegister, ['VENTA_EMITIR']],
  ['ventas', 'Facturas', faFileInvoiceDollar, ['VENTA_EMITIR', 'REPORTE_CONSULTAR']],
  ['comprar', 'Comprar', faTruckRampBox, ['COMPRA_REGISTRAR']],
  ['productos', 'Productos y existencias', faBoxesStacked, []],
  ['terceros', 'Clientes y proveedores', faAddressBook, []],
  ['reservas', 'Reservas y dañados', faTriangleExclamation, ['RESERVA_GESTIONAR', 'ESTADO_DANADO']],
  ['kardex', 'Kárdex', faListOl, ['PRODUCTO_EDITAR', 'REPORTE_CONSULTAR']],
  ['cierres', 'Cierres mensuales', faCalendarCheck, []],
  ['reportes', 'Reportes', faChartLine, ['REPORTE_CONSULTAR']],
  ['migracion', 'Migración Excel', faFileExcel, ['MIGRACION_EJECUTAR']],
  ['auditoria', 'Auditoría', faShieldHalved, ['AUDITORIA_CONSULTAR']],
  ['usuarios', 'Usuarios', faUsers, ['USUARIO_ADMINISTRAR']],
  ['offline', 'Sin internet', faWifi, ['VENTA_EMITIR']],
];

const rutaActual = () => location.hash.replace('#/', '') || 'inicio';
const esMovil = () => window.matchMedia('(max-width: 760px)').matches;

export default function App() {
  const avisar = useAvisar();
  const [ruta, setRuta] = useState(rutaActual);
  const [enLinea, setEnLinea] = useState(navigator.onLine);
  const [pendientes, setPendientes] = useState(local.cola().length);
  const [menuAbierto, setMenuAbierto] = useState(false);
  const s = sesion.vigente();

  const refrescarEstado = useCallback(() => { setEnLinea(navigator.onLine); setPendientes(local.cola().length); }, []);

  const intentarSincronizar = useCallback(async () => {
    if (!navigator.onLine || !sesion.vigente() || !local.cola().length) return;
    try {
      const r = await sincronizar();
      const conflictos = r.filter(x => x.estado === 'CONFLICTO').length;
      avisar(`Sincronizadas ${r.length - conflictos} venta(s)` + (conflictos ? `; ${conflictos} en conflicto para revisión` : ''), !!conflictos);
      await refrescarCatalogo();
    } catch { /* se reintenta en el siguiente ciclo */ }
    refrescarEstado();
  }, [avisar, refrescarEstado]);

  useEffect(() => {
    const alCambiarRuta = () => { setRuta(rutaActual()); if (esMovil()) setMenuAbierto(false); };
    const alConectar = () => { refrescarEstado(); intentarSincronizar(); };
    window.addEventListener('hashchange', alCambiarRuta);
    window.addEventListener('online', alConectar);
    window.addEventListener('offline', refrescarEstado);
    window.addEventListener('cola-cambio', refrescarEstado);
    const t = setInterval(intentarSincronizar, 60000);
    return () => {
      window.removeEventListener('hashchange', alCambiarRuta);
      window.removeEventListener('online', alConectar);
      window.removeEventListener('offline', refrescarEstado);
      window.removeEventListener('cola-cambio', refrescarEstado);
      clearInterval(t);
    };
  }, [intentarSincronizar, refrescarEstado]);

  useEffect(() => {
    if (!s && ruta !== 'login') location.hash = '#/login';
    document.getElementById('vista')?.focus();
  }, [s, ruta]);

  const Pagina = s ? (paginaDe(ruta) || PAGINAS.inicio) : PAGINAS.login;
  const parametro = ruta.split('/')[1];

  return (
    <>
      <header className="barra">
        {s && <button id="btn-menu" className="icono" aria-label="Menú" onClick={() => setMenuAbierto(m => !m)}><Icono i={faBars} /></button>}
        <strong>Inventario Ferretería</strong>
        <span id="estado-red" className={'pastilla' + (enLinea ? '' : ' aviso')}>
          <Icono i={faWifi} /> {enLinea ? 'En línea' : 'Sin internet'}</span>
        {pendientes > 0 && <span id="pendientes" className="pastilla aviso">{pendientes} venta(s) por sincronizar</span>}
        <span className="espacio" />
        {s && <span id="usuario">{s.nombre}</span>}
        {s && <Boton id="btn-salir" clase="secundario" icono={faRightFromBracket} title="Salir"
          onClick={() => { sesion.cerrar(); location.hash = '#/login'; setRuta('login'); }}>Salir</Boton>}
      </header>
      <div className="cuerpo">
        {s && (
          <nav id="menu" className={menuAbierto ? 'abierto' : ''}>
            {MENU.filter(([, , , p]) => !p.length || sesion.puede(...p)).map(([id, nombre, icono]) => (
              <a key={id} href={`#/${id}`} className={id === ruta.split('/')[0] ? 'activo' : ''}><Icono i={icono} /> {nombre}</a>))}
          </nav>
        )}
        {/* La clave fuerza una página nueva en cada ruta, con su estado limpio. */}
        <main id="vista" tabIndex={-1} key={ruta}>
          <Pagina parametro={parametro} alIngresar={() => setRuta(rutaActual())} />
        </main>
      </div>
    </>
  );
}
