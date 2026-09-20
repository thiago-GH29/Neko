# ANC Experimental — Redmi Note 8

Proyecto Android experimental que toma el audio del micrófono del teléfono,
invierte la señal y la reproduce por la salida de audio del sistema.

## Importante

Esto NO es una implementación de ANC real. Sirve para experimentar con el
principio de cancelación por interferencia destructiva.

La ruta:

micrófono del teléfono → procesamiento → Bluetooth/auriculares → oído

tiene latencia. Por eso la señal invertida normalmente no llegará al oído en
el momento exacto del ruido original. Con Bluetooth el retraso puede ser
especialmente grande.

## Cómo abrir

1. Instala Android Studio en una PC.
2. Descomprime el ZIP.
3. Abre la carpeta `ANC_Experimental_RedmiNote8`.
4. Espera a que Gradle sincronice.
5. Conecta el Redmi Note 8 por USB y activa Depuración USB.
6. Ejecuta la aplicación.
7. Conecta tus auriculares antes de pulsar INICIAR.
8. Empieza con volumen muy bajo.

## Seguridad

No uses la aplicación con volumen alto. Una mala configuración puede producir
realimentación, sonidos fuertes o molestos. No uses el experimento mientras
conduces ni en situaciones donde necesites escuchar alarmas o vehículos.

## Próximo paso técnico

Una versión más avanzada podría usar un filtro adaptativo (por ejemplo LMS/NLMS)
y estimar la respuesta de los auriculares para compensar la latencia, en lugar
de simplemente invertir cada muestra.
