import { api, sesion } from '../api.js';
import { dinero, fecha, num } from '../formato.js';
import { Estado, Tabla, useCarga } from '../componentes.jsx';

export default function Reportes() {
  const costos = sesion.puede('COSTO_VER');
  const [d, , error] = useCarga(async () => {
    const [diarias, garantias, margen, valoracion] = await Promise.all([
      api('/api/reportes/ventas-diarias'), api('/api/reportes/garantias-vigentes'),
      costos ? api('/api/reportes/margen') : [], costos ? api('/api/reportes/valoracion') : []]);
    return { diarias, garantias, margen, valoracion };
  });
  return (
    <>
      <h1>Reportes</h1>
      <Estado datos={d} error={error}>{d && <>
        <h2>Ventas de los últimos 30 días</h2>
        <Tabla filas={d.diarias} columnas={[['dia', 'Día'], ['facturas', 'Facturas', null, 'num'], ['base', 'Base', dinero, 'num'],
          ['iva', 'IVA', dinero, 'num'], ['total', 'Total', dinero, 'num']]} />
        {costos && <>
          <h2>Margen por referencia (30 días)</h2>
          <Tabla filas={d.margen} columnas={[['referencia', 'Referencia'], ['venta', 'Venta', dinero, 'num'], ['costo', 'Costo', dinero, 'num'], ['margen', 'Margen', dinero, 'num']]} />
          <h2>Valoración del inventario (costo promedio)</h2>
          <Tabla filas={d.valoracion} columnas={[['producto', 'Producto'], ['marca', 'Marca'], ['presentacion', 'Presentación'],
            ['total', 'Cantidad', num, 'num'], ['costo_promedio', 'Costo', dinero, 'num'], ['valor', 'Valor', dinero, 'num']]} />
        </>}
        <h2>Garantías vigentes</h2>
        <Tabla filas={d.garantias} columnas={[['factura', 'Factura'], ['cliente', 'Cliente'], ['referencia', 'Referencia'], ['fecha_fin_garantia', 'Hasta', fecha]]} />
      </>}</Estado>
    </>
  );
}
