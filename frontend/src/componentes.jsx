// Piezas compartidas: avisos, tabla genérica, carga de datos, botones con ícono y campos de formulario.
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleCheck, faCircleExclamation, faSpinner, faXmark } from '@fortawesome/free-solid-svg-icons';

// ------------------------------------------------------------------ avisos
const ContextoAvisos = createContext(() => {});
export const useAvisar = () => useContext(ContextoAvisos);

export function ProveedorAvisos({ children }) {
  const [avisos, setAvisos] = useState([]);
  const capa = useRef(null);
  const quitar = useCallback(id => setAvisos(a => a.filter(x => x.id !== id)), []);
  // Los avisos van en la capa superior del navegador para verse también sobre una ventana emergente abierta.
  useEffect(() => {
    const c = capa.current;
    if (!c?.showPopover) return;
    if (c.matches(':popover-open')) c.hidePopover();
    if (avisos.length) c.showPopover();
  }, [avisos]);
  const avisar = useCallback((mensaje, error = false) => {
    const id = Math.random();
    const texto = String(mensaje || '').trim() || (error ? 'Ocurrió un error inesperado. Intente de nuevo.' : 'Listo');
    setAvisos(a => [...a.filter(x => x.mensaje !== texto), { id, mensaje: texto, error }]);
    setTimeout(() => quitar(id), error ? 8000 : 4000);
  }, [quitar]);
  return (
    <ContextoAvisos.Provider value={avisar}>
      {children}
      <div id="aviso" ref={capa} popover="manual" aria-live="polite">
        {avisos.map(a => (
          <div key={a.id} className={a.error ? 'error' : ''} role={a.error ? 'alert' : 'status'}>
            <Icono i={a.error ? faCircleExclamation : faCircleCheck} />
            <span>{a.mensaje}</span>
            <button type="button" className="cerrar" aria-label="Cerrar aviso" onClick={() => quitar(a.id)}><Icono i={faXmark} /></button>
          </div>
        ))}
      </div>
    </ContextoAvisos.Provider>
  );
}

/** Envuelve una acción asíncrona: muestra el error como aviso en vez de dejarlo sin manejar. */
export function useAccion() {
  const avisar = useAvisar();
  return useCallback(fn => async (...a) => {
    try { return await fn(...a); } catch (e) { avisar(e.message, true); }
  }, [avisar]);
}

/** Carga datos al montar (y cuando cambian las dependencias). Devuelve [datos, recargar, error]. */
export function useCarga(cargar, deps = []) {
  const [estado, setEstado] = useState({ datos: undefined, error: null });
  const recargar = useCallback(async () => {
    try { setEstado({ datos: await cargar(), error: null }); } catch (e) { setEstado(s => ({ ...s, error: e })); }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { recargar(); }, [recargar]);
  return [estado.datos, recargar, estado.error];
}

// ------------------------------------------------------------------ presentación
/** Error dentro de la pantalla o del formulario, con el mismo aspecto en todo el sistema. */
export const MensajeError = ({ children }) =>
  children ? <p className="mensaje-error" role="alert"><Icono i={faCircleExclamation} /> <span>{children}</span></p> : null;

export const Icono = ({ i, ...p }) => <FontAwesomeIcon icon={i} fixedWidth {...p} />;

export function Boton({ icono, children, clase, ...p }) {
  return <button className={clase} {...p}>{icono && <Icono i={icono} />}{children && <span>{children}</span>}</button>;
}

export const Cargando = () => <p className="suave"><Icono i={faSpinner} spin /> Cargando…</p>;

export function Estado({ error, datos, children }) {
  if (error) return <MensajeError>{error.message}</MensajeError>;
  if (datos === undefined) return <Cargando />;
  return children;
}

/** Campo con etiqueta arriba, como en el resto de formularios. */
export const Campo = ({ etiqueta, children }) => <label>{etiqueta}{children}</label>;

/**
 * Tabla genérica. columnas = [[clave, título, formato?, 'num'?]]; el formato recibe (valor, fila) y puede
 * devolver texto o JSX. acciones(fila, i) agrega una última columna.
 */
export function Tabla({ filas, columnas, acciones }) {
  if (!filas?.length) return <p className="suave">Sin resultados.</p>;
  return (
    <div className="tabla"><table>
      <thead><tr>{columnas.map(([k, t, , c]) => <th key={k + t} className={c}>{t}</th>)}{acciones && <th />}</tr></thead>
      <tbody>{filas.map((f, i) => (
        <tr key={f.id ?? f.uuid ?? i}>
          {columnas.map(([k, t, fmt, c]) => <td key={k + t} className={c}>{fmt ? fmt(f[k], f) : f[k]}</td>)}
          {acciones && <td className="acciones">{acciones(f, i)}</td>}
        </tr>))}
      </tbody>
    </table></div>
  );
}

/** Lee los campos de un formulario como objeto { nombre: valor recortado }. */
export const valores = form => Object.fromEntries([...new FormData(form)].map(([k, v]) => [k, typeof v === 'string' ? v.trim() : v]));

/**
 * Ventana emergente sobre la página (dialog nativo): se cierra con Esc, con la X o tocando fuera.
 * Se muestra al montarla; el padre la quita llamando alCerrar.
 */
export function Ventana({ titulo, alCerrar, children, ancha }) {
  const ref = useRef(null);
  useEffect(() => {
    const d = ref.current;
    if (!d.open) d.showModal();
    return () => d.open && d.close();
  }, []);
  return (
    <dialog ref={ref} className={'ventana' + (ancha ? ' ancha' : '')} onClose={alCerrar}
      onClick={e => e.target === ref.current && alCerrar()}>
      <header><h2>{titulo}</h2>
        <button className="icono" data-a="cerrar" aria-label="Cerrar" onClick={alCerrar}><Icono i={faXmark} /></button></header>
      <div className="contenido">{children}</div>
    </dialog>
  );
}
