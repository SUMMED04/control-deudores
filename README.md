# Sistema de Control de Deudores

Aplicación web de una sola página para llevar el control de dinero prestado: quién debe,
cuánto, desde cuándo y qué ha pagado. Funciona sin servidor y sin internet, se abre
haciendo doble clic en `index.html` y guarda todo en el navegador (`localStorage`).

## Qué hace

- **Deudores ilimitados.** Se agregan, editan y eliminan desde la misma pantalla.
- **Dos tipos de movimiento.** Un préstamo nuevo (`CARGO`) suma a la deuda; un abono
  (`PAGO`) la resta. Todo queda en el historial del deudor.
- **Fecha y hora automáticas.** Al registrar un cobro se estampa el momento exacto.
  No se escribe a mano.
- **Fecha límite configurable.** Por defecto el día 15 de cada mes, ajustable por deudor.
  Si el mes no tiene ese día (31 en febrero) se usa el último día del mes.
- **Estado calculado.** Al día / Por vencer / Atrasado / Pagado, con los días de mora.
  La franja de color en la parte superior de cada tarjeta permite escanear la pantalla
  de un vistazo.
- **Panel de avisos.** Si alguien se pasó de su fecha de pago aparece arriba del todo,
  con los días de atraso y el monto. Un clic sobre el aviso abre directamente el cobro,
  y en cuanto se registra el pago el aviso desaparece solo, porque el estado se
  recalcula. Opcionalmente manda una notificación del navegador, como máximo una al día.
- **Buscador y filtros** por nombre, teléfono y estado, con cuatro criterios de orden.
- **WhatsApp.** Con el teléfono cargado aparece un enlace que abre el chat con el
  recordatorio del saldo ya escrito.
- **Notas** por deudor.
- **Gráficos** de pendiente vs cobrado, cobros acumulados y distribución del saldo.
  Con más de 8 deudores se agrupan los menores en "Otros" para que el eje no se sature.
- **Exportar a Excel** en `.xlsx` real (no un HTML disfrazado), con dos hojas:
  *Resumen* con los totales, la tabla de cartera y las gráficas insertadas como imagen,
  y *Movimientos* con el detalle completo, panel congelado y autofiltro.
- **Exportar a PDF**: estado de cuenta con resumen de cartera y un bloque por deudor.
- **Modo oscuro** y logo personalizable.

## Decisión de diseño importante

**El saldo no se guarda.** Se calcula sumando y restando los movimientos cada vez que se
dibuja la pantalla. Así nunca puede haber dos números distintos diciendo cosas distintas:
si se borra un movimiento del historial, el saldo se corrige solo.

## Migración desde la versión anterior

La versión vieja tenía dos deudores fijos escritos en el código (`deudor1` y `deudor2`).
Al abrir esta versión por primera vez, esos datos se convierten al formato nuevo
automáticamente y se guarda una copia intacta en la clave `respaldo_pre_migracion` de
`localStorage` antes de tocar nada. Las claves viejas solo se borran cuando el guardado
nuevo ya funcionó.

Los pagos antiguos no tenían hora registrada; se les asigna las 12:00 (mediodía, neutral
respecto a la fecha de corte) y se marcan con la nota "Migrado (hora no registrada)".

## Estructura de datos

```js
deudor = {
  id, nombre, telefono, notas,
  diaPago,              // 1-31, día de corte mensual
  pagoMensual,          // cuota sugerida, solo prellena el formulario
  fechaCreacion,        // ISO
  movimientos: [
    { id, tipo: 'CARGO' | 'PAGO', ts, monto, nota }   // ts = ISO con hora
  ]
}
```

## Archivos

| Archivo | Qué es |
|---|---|
| `index.html` | La aplicación completa: HTML, CSS y JS en un solo archivo |
| `lib/chart.min.js` | Chart.js 3.9.1, para las gráficas |
| `lib/exceljs.min.js` | ExcelJS 4.4.0, para generar el `.xlsx` con estilos e imágenes |
| `portada.jpg` | Logo por defecto del encabezado |
| `_version_anterior.html` | La versión previa de 2 deudores fijos, guardada como referencia |

Las dos librerías están guardadas en `lib/` en vez de cargarse desde un CDN, porque la
app se abre con doble clic desde el disco y tiene que seguir funcionando sin internet.
Con un CDN, el botón de Excel fallaría en silencio al no haber conexión.

ExcelJS no puede crear gráficos nativos de Excel (ninguna librería gratuita de navegador
puede). Las gráficas se insertan como imagen PNG generada por Chart.js, que para leer un
reporte da igual, pero conviene saberlo: no son editables dentro de Excel.

## Cómo usarlo

Doble clic en `index.html`. Los datos se guardan en el navegador donde lo abras, así que
si lo abres en otro navegador o en modo incógnito verás la lista vacía. Para conservar el
historial fuera del navegador, exporta el Excel.
