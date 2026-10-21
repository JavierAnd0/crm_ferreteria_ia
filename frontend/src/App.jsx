// Arranque de la interfaz: sesión, menú según permisos, enrutador por hash y estado de conexión.
import { useCallback, useEffect, useState } from 'react';
import {
  faAddressBook, faBars, faBoxesStacked, faCalendarCheck, faCashRegister, faChartLine, faFileExcel,
  faFileInvoiceDollar, faHouse, faListOl, faRightFromBracket, faShieldHalved, faTriangleExclamation,
  faTruckRampBox, faUsers, faWifi, faClipboardList, faPlug, faScrewdriverWrench,
} from '@fortawesome/free-solid-svg-icons';
import { sesion } from './api.js';
import { local, refrescarCatalogo, sincronizar } from './offline.js';
import { Boton, Icono, useAvisar } from './componentes.jsx';
import { PAGINAS, paginaDe } from './paginas/index.js';

// Los textos sueltos son títulos de sección del menú, como en la plantilla.
const MENU = [
  ['inicio', 'Inicio', faHouse, []],
  'Operación',
  ['vender', 'Vender', faCashRegister, ['VENTA_EMITIR']],
  ['ventas', 'Facturas', faFileInvoiceDollar, ['VENTA_EMITIR', 'REPORTE_CONSULTAR']],
  ['comprar', 'Comprar', faTruckRampBox, ['COMPRA_REGISTRAR']],
  ['compras', 'Compras registradas', faClipboardList, ['COMPRA_REGISTRAR', 'COSTO_VER']],
  ['offline', 'Sin internet', faWifi, ['VENTA_EMITIR']],
  'Inventario',
  ['productos', 'Productos y existencias', faBoxesStacked, []],
  ['terceros', 'Clientes y proveedores', faAddressBook, []],
  ['reservas', 'Reservas y dañados', faTriangleExclamation, ['RESERVA_GESTIONAR', 'ESTADO_DANADO']],
  ['kardex', 'Kárdex', faListOl, ['PRODUCTO_EDITAR', 'REPORTE_CONSULTAR']],
  ['cierres', 'Cierres mensuales', faCalendarCheck, []],
  ['reportes', 'Reportes', faChartLine, ['REPORTE_CONSULTAR']],
  'Administración',
  ['migracion', 'Migración Excel', faFileExcel, ['MIGRACION_EJECUTAR']],
  ['auditoria', 'Auditoría', faShieldHalved, ['AUDITORIA_CONSULTAR']],
  ['usuarios', 'Usuarios', faUsers, ['USUARIO_ADMINISTRAR']],
  ['integraciones', 'Integraciones', faPlug, ['USUARIO_ADMINISTRAR']],
];

/** Quita los títulos de sección que quedaron sin ninguna opción visible para el usuario. */
const menuVisible = () => {
  const visibles = MENU.filter(m => typeof m === 'string' || !m[3].length || sesion.puede(...m[3]));
  return visibles.filter((m, i) => typeof m !== 'string' || (visibles[i + 1] && typeof visibles[i + 1] !== 'string'));
};

const iniciales = nombre => String(nombre || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');

const rutaActual = () => location.hash.replace('#/', '') || 'inicio';
const esMovil = () => window.matchMedia('(max-width: 1199px)').matches;

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

  if (!s) return <main id="vista" className="pagina-ingreso" tabIndex={-1} key={ruta}><Pagina alIngresar={() => setRuta(rutaActual())} /></main>;

  return (
    <div className="cuerpo">
      <nav id="menu" className={menuAbierto ? 'abierto' : ''}>
        <a className="marca" href="#/inicio"><span className="logo"><Icono i={faScrewdriverWrench} /></span>Ferretería</a>
        {menuVisible().map(m => typeof m === 'string' ? <div key={m} className="seccion">{m}</div> : (
          <a key={m[0]} href={`#/${m[0]}`} className={'item' + (m[0] === ruta.split('/')[0] ? ' activo' : '')}><Icono i={m[2]} /> {m[1]}</a>))}
      </nav>
      <div id="velo" onClick={() => setMenuAbierto(false)} />
      <div className="principal">
        <header className="barra">
          <button id="btn-menu" className="icono" aria-label="Menú" onClick={() => setMenuAbierto(m => !m)}><Icono i={faBars} /></button>
          <span className="titulo-app">Inventario Ferretería</span>
          <span id="estado-red" className={'pastilla' + (enLinea ? '' : ' aviso')}>
            <Icono i={faWifi} /> {enLinea ? 'En línea' : 'Sin internet'}</span>
          {pendientes > 0 && <span id="pendientes" className="pastilla aviso">{pendientes} venta(s) por sincronizar</span>}
          <span className="espacio" />
          <span className="usuario"><span className="avatar">{iniciales(s.nombre)}</span><span id="usuario">{s.nombre}</span></span>
          <Boton id="btn-salir" clase="texto" icono={faRightFromBracket} title="Salir"
            onClick={() => { sesion.cerrar(); location.hash = '#/login'; setRuta('login'); }}>Salir</Boton>
        </header>
        {/* La clave fuerza una página nueva en cada ruta, con su estado limpio. */}
        <main id="vista" tabIndex={-1} key={ruta}>
          <Pagina parametro={parametro} alIngresar={() => setRuta(rutaActual())} />
        </main>
      </div>
    </div>
  );
}
