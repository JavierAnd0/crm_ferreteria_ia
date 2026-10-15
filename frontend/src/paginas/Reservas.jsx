import { faBookmark, faHeartCrack, faLockOpen } from '@fortawesome/free-solid-svg-icons';
import { api, sesion } from '../api.js';
import { uuid } from '../offline.js';
import { fecha, num } from '../formato.js';
import { Boton, Campo, Estado, Icono, Tabla, useAccion, useAvisar, useCarga, valores } from '../componentes.jsx';

const porCodigo = codigo => api(`/api/referencias/codigo/${encodeURIComponent(codigo)}`);

export default function Reservas() {
  const accion = useAccion();
  const avisar = useAvisar();
  const gestiona = sesion.puede('RESERVA_GESTIONAR');
  const [filas, recargar, error] = useCarga(() => api('/api/reservas'));

  const reservar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    const r = await porCodigo(v.codigo);
    const c = (await api(`/api/terceros?clientes=true&q=${encodeURIComponent(v.cliente)}&limite=1`))[0];
    if (!c) throw new Error('Cliente no encontrado');
    await api('/api/reservas', { metodo: 'POST', cuerpo: { uuid: uuid(), clienteId: c.id, referenciaId: r.id, cantidadBase: Number(v.cantidad) } });
    avisar('Reserva registrada'); e.target.reset(); recargar();
  });
  const danar = accion(async e => {
    e.preventDefault();
    const v = valores(e.target);
    const r = await porCodigo(v.codigo);
    await api('/api/existencias/cambios-estado', { metodo: 'POST', cuerpo: { referenciaId: r.id, loteId: v.lote ? Number(v.lote) : null,
      cantidadBase: Number(v.cantidad), accion: v.accion, motivo: v.motivo } });
    avisar('Cambio de estado registrado'); e.target.reset();
  });
  const liberar = accion(async id => {
    await api(`/api/reservas/${id}/liberar`, { metodo: 'POST' }); avisar('Reserva liberada'); recargar();
  });

  return (
    <>
      <h1>Reservas y dañados</h1>
      {gestiona && <form className="panel fila" id="reservar" onSubmit={reservar}><strong><Icono i={faBookmark} /> Reservar</strong>
        <Campo etiqueta="Código de barras"><input name="codigo" required /></Campo>
        <Campo etiqueta="Cantidad (unidad base)"><input name="cantidad" type="number" step="any" required /></Campo>
        <Campo etiqueta="Cédula/NIT del cliente"><input name="cliente" required /></Campo>
        <Boton>Reservar</Boton></form>}
      {sesion.puede('ESTADO_DANADO') && <form className="panel fila" id="danado" onSubmit={danar}><strong><Icono i={faHeartCrack} /> Dañados</strong>
        <Campo etiqueta="Código de barras"><input name="codigo" required /></Campo>
        <Campo etiqueta="Id de lote (si aplica)"><input name="lote" size={6} /></Campo>
        <Campo etiqueta="Cantidad"><input name="cantidad" type="number" step="any" required /></Campo>
        <Campo etiqueta="Acción"><select name="accion"><option value="DANAR">Marcar dañado</option><option value="RECUPERAR">Recuperar</option></select></Campo>
        <Campo etiqueta="Motivo"><input name="motivo" required /></Campo>
        <Boton>Registrar</Boton></form>}
      <h2>Reservas activas</h2>
      <Estado datos={filas} error={error}>
        <Tabla filas={filas} columnas={[['id', 'N.º'], ['referencia', 'Referencia'], ['codigo_lote', 'Lote'],
          ['cantidad_base', 'Cantidad', num, 'num'], ['cliente', 'Cliente'], ['vence_en', 'Vence', fecha]]}
          acciones={gestiona ? f => <Boton clase="secundario" data-a="liberar" icono={faLockOpen} onClick={() => liberar(f.id)}>Liberar</Boton> : undefined} />
      </Estado>
    </>
  );
}
