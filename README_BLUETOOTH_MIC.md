# Versión con micrófono Bluetooth

Esta versión intenta usar el micrófono del auricular TWS en lugar del micrófono del Redmi Note 8.

## Cómo probarla

1. Empareja y conecta los TWS antes de abrir la app.
2. Concede permiso de micrófono y Bluetooth si Android lo solicita.
3. Comprueba que aparezca `Micrófono Bluetooth detectado`.
4. Pulsa **INICIAR ANC EXPERIMENTAL**.
5. Empieza con el volumen y el anti-ruido muy bajos.
6. Prueba primero con un ventilador o un ruido grave y constante.

## Importante

Los TWS Bluetooth normalmente usan A2DP para música, pero el micrófono suele funcionar mediante HFP/HSP (Bluetooth SCO). Al activar el micrófono del TWS, Android puede cambiar a ese modo y la calidad de audio puede bajar bastante.

El algoritmo actual solamente invierte la onda capturada. No conoce la distancia, fase ni respuesta acústica entre el altavoz y el oído, por lo que no es ANC verdadero. El objetivo es experimentar con el micrófono del propio auricular y medir qué tan cerca podemos llegar.
