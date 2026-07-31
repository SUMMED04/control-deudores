# Sistema de Control de Deudores

Dos versiones que comparten las mismas reglas:

| | Dónde | Para qué |
|---|---|---|
| **Web** | `index.html` | Escritorio. Genera el PDF y el Excel. |
| **Android** | `android/` | Móvil. Avisa el día de cobro aunque esté cerrada. |

El APK se compila solo en GitHub Actions al tocar `android/` y queda en el release
`ultima-version` como `SummedDeudores.apk`.

## Diferencia que importa entre las dos

La web solo puede avisarte cuando la abres. La app de Android programa una alarma real
con `setAlarmClock`, así que el aviso llega el día de cobro aunque no la hayas abierto.
Se usa `setAlarmClock` y no `setExactAndAllowWhileIdle` porque es lo único que atraviesa
el modo Doze de forma fiable: con el teléfono dormido, un recordatorio del día 15 se
retrasaría horas. La alarma se vuelve a programar tras cada cobro, al reiniciar el
teléfono y al actualizar la app, porque es de un solo uso y no sobrevive al reinicio.

Los datos de cada versión son independientes: la web guarda en el navegador y la app en
el teléfono. No se sincronizan.

## La app de Android

Además de la lista, el detalle de cada deudor y los reportes, tiene:

- **Hora del aviso** editable, **tono** elegible con el selector del sistema y **duración
  del sonido** de 5, 10, 20 o 30 segundos, con un botón para probarlo.
- **Logo** propio, que sale arriba de la lista y en la cabecera del PDF.
- **Imagen de fondo** con intensidad regulable.
- **Tema** automático, claro u oscuro.
- **Exportar a PDF y a Excel** desde el móvil, con el selector de compartir.

El tono lo reproduce `ServicioAviso`, un servicio en primer plano, y no el
`BroadcastReceiver`: un receiver vive unos segundos y cortaría el sonido a mitad. El canal
de notificación va en silencio a propósito, porque si sonara él también se oirían dos
tonos a la vez. Si el tono elegido ya no existe, se cae al de alarma del sistema en vez de
quedarse mudo.

**La imagen de fondo no aparece en los reportes.** No hay que acordarse de excluirla: el
PDF se dibuja con `PdfDocument` y el Excel se escribe como XML, así que ninguno de los dos
hereda nada de la pantalla. Solo sale lo que se dibuja a propósito, y el logo sí se dibuja.

El Excel de la app no usa Apache POI, que pesaría más que el resto de la app junta.
`EscritorXlsx` genera el `.xlsx` a mano, que no es más que un zip de XML, con las mismas
tres hojas que la web y el teléfono guardado como texto para no perder el cero inicial.


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
- **Exportar a Excel** en `.xlsx` real (no un HTML disfrazado), con tres hojas:
  *Panel* con los cuatro indicadores, la tabla de resumen y las gráficas;
  *Deudores* con la ficha completa de cada uno; *Pagos* con todos los movimientos.
  Las tres tablas son tablas de Excel de verdad, con botones de filtro y bandas,
  y la columna Avance lleva barras de formato condicional nativas, que se recalculan
  solas si se edita un número.
- **Exportar a PDF**: una hoja por deudor con su desglose de deuda y tres gráficos
  (evolución del saldo, avance del pago, y deuda contra pagos por mes), precedidas
  de una página de resumen de cartera.
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
