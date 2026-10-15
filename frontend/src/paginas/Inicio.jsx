import { faBoxOpen, faCloudArrowUp, faHourglassHalf, faMoneyBillWave } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { local } from '../offline.js';
import { dinero, fecha, hoy, num } from '../formato.js';
import { Estado, Icono, Tabla, useCarga } from '../componentes.jsx';

const Tarjeta = ({ icono, titulo, valor }) => (
  <div className="tarjeta"><div className="suave"><Icono i={icono} /> {titulo}</div><div className="valor">{valor}</div></div>);

export default function Inicio() {
  const reportes = sesion.puede('REPORTE_CONSULTAR');
  const [d, , error] = useCarga(async () => {
    const [bajos, lotes, ventas] = await Promise.all([
      api('/api/referencias?bajoMinimo=true&limite=50'), api('/api/lotes/por-vencer?dias=90'),
      reportes ? api(`/api/reportes/ventas-diarias?desde=${hoy()}&hasta=${hoy()}`) : []]);
    return { bajos, lotes, total: ventas.reduce((s, v) => s + Number(v.total), 0) };
  });
  return (
    <>
      <h1>Inicio</h1>
      <Estado datos={d} error={error}>{d && <>
        <div className="tarjetas">
          <Tarjeta icono={faMoneyBillWave} titulo="Ventas de hoy" valor={reportes ? dinero(d.total) : '—'} />
          <Tarjeta icono={faBoxOpen} titulo="Referencias bajo el mínimo" valor={d.bajos.length} />
          <Tarjeta icono={faHourglassHalf} titulo="Lotes que vencen en 90 días" valor={d.lotes.length} />
          <Tarjeta icono={faCloudArrowUp} titulo="Ventas sin sincronizar" valor={local.cola().length} />
        </div>
        <h2>Bajo el stock mínimo</h2>
        <Tabla filas={d.bajos} columnas={[['codigoBarras', 'Código'], ['producto', 'Producto'], ['marca', 'Marca'],
          ['presentacion', 'Presentación'], ['disponible', 'Disponible', num, 'num'], ['stockMinimo', 'Mínimo', num, 'num']]} />
        <h2>Próximos a vencer</h2>
        <Tabla filas={d.lotes} columnas={[['codigo_lote', 'Lote'], ['producto', 'Producto'], ['marca', 'Marca'],
          ['fecha_vencimiento', 'Vence', fecha], ['dias_para_vencer', 'Días', null, 'num'], ['cantidad', 'Cantidad', num, 'num']]} />
      </>}</Estado>
    </>
  );
}
