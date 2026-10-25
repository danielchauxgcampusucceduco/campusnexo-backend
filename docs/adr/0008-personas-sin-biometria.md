# ADR 0008: Docentes y estudiantes sin biometría

- Estado: aceptada
- Fecha: 2026-10-24

## Contexto

La administración necesita saber quién es responsable de cada reserva y si hay clase de verdad (un docente frente a estudiantes). Reconocer a las personas por su cara sería un tratamiento de datos sensibles (Ley 1581 de 2012) desproporcionado para un sistema operativo de salones.

## Decisión

- `Persona` es una clase abstracta con `Docente` y `Estudiante`; solo guarda el código institucional, el nombre y la adscripción. Cada tipo decide qué puede reservar y por cuánto tiempo (polimorfismo), y la reserva le pregunta a su titular.
- **Quién** es la persona lo dice el check-in con su código en el kiosco de la puerta (clave de dispositivo), no la cámara.
- **Qué rol** cumple en el salón lo dice **dónde** está: una persona en la franja frente al tablero cuenta como docente y una en un pupitre como estudiante (`Presence`). La cámara solo envía posiciones en el piso; ninguna imagen sale del salón.
- Las rutas que leen personas son de ADMIN, también para lectura.

## Consecuencias

- `CLASE_EN_CURSO` exige docente en zona y al menos un estudiante, sin identificar a nadie.
- Una persona sentada en la franja del tablero cuenta como docente; la franja es una perilla de calibración por salón.
- Agregar un tipo de persona (p. ej. administrativo) es una subclase, sin tocar el servicio de reservas.

## Alternativas consideradas

- Reconocimiento facial: descartado por privacidad y proporcionalidad.
- Cuentas de usuario para docentes y estudiantes: innecesarias para un sistema operado por la administración.
