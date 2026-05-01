BROCAM - PRODUCT ROADMAP & BACKLOG

Visión: Herramienta P2P B2B de latencia ultra-baja para asistencia remota y supervisión técnica en campo, operando 100% offline.

🟢 FASE 1: Estabilización y MVP Técnico (Completado)

Objetivo: Lograr una conexión estable de baja latencia con captura de evidencia forense.

[x] Motor de Streaming P2P: Transmisión fluida usando Nearby Connections (P2P_STAR) y compresión MJPEG.

[x] Manejo de Latencia: Implementación de canal CONFLATED para evitar cuellos de botella en redes lentas.

[x] UI/UX Base: Pantalla de selección de roles con diseño "Industrial Dark" (Material Design 3).

[x] Corrección de Hardware: Corrección de la rotación física del sensor (setTargetRotation y Matrix).

[x] Captura Forense (Zero-Crop): Disparo independiente en máxima resolución (Full HD / 4:3 puro) ignorando el recorte de pantalla.

[x] Historial Local: Base de datos ligera (SharedPreferences + Gson) para reconexión rápida.

🟡 FASE 2: UX Operativa y Ahorro de Energía (Sprint Actual)

Objetivo: Adaptar la herramienta para jornadas largas de trabajo en campo y mejorar el feedback entre dispositivos.

[X] Aviso Remoto de Captura: Confirmación visual en la pantalla del "Control" cuando el "Lente" guarda la foto exitosamente.

[X] Modo "Pantalla Oscura" (Lente): Ahorro extremo de batería atenuando el brillo del sistema y bloqueando toques accidentales para transmisión continua.

[X] Selector de Calidad UI: Botón funcional en el Drawer de Configuración para alternar SD/HD antes o durante la transmisión.

[X] Manejo de Desconexiones: UI clara cuando el Lente o el Control pierden señal inesperadamente.

🔵 FASE 3: "Killer Features" B2B & Pro Photo (Diferenciadores de Mercado)
Objetivo: Implementar las herramientas visuales que justifican la monetización comercial e industrial.

[ ] Puntero AR / Telestration: Permitir al "Control" tocar su pantalla y que aparezca un marcador rojo en la vista en vivo del "Lente".
[ ] Congelar y Dibujar (Freeze & Annotate): Botón para capturar el frame actual, dibujar indicaciones y enviarlas a la pantalla del técnico para tareas precisas.
[ ] Intercomunicador de Voz: Canal STREAM paralelo de Nearby para audio bidireccional integrado.
[ ] Controles de Exposición (Modo Fotógrafo): Ajuste manual de ISO, Velocidad de Obturación y Balance de Blancos operado desde el Control.
[ ] Flash Dinámico: Control de intensidad de la linterna / Modo SOS.

🟣 FASE 4: Escalabilidad, Datos y Nivel Enterprise
Objetivo: Dar el salto al sector corporativo y peritaje con datos validados.

[ ] Migración a H.265 (MediaCodec): Reemplazar MJPEG por hardware para reducir drásticamente el peso de red y mejorar FPS.
[ ] Marca de Agua + Evidencia: Imprimir coordenadas GPS, fecha y hora (Metadatos) directamente en los píxeles de la foto HD capturada para peritajes.
[ ] Lector Inteligente (OCR/Barcode): Extracción de números de serie y códigos de barras en el Control vía ML Kit usando el Lente remoto.
[ ] Modo Enterprise (N-a-1 y 1-a-N): Un Control para múltiples Lentes (Centro de Comando) o un Lente transmitiendo a múltiples controles.
[ ] Captura RAW y Grillas: Soporte para DNG y superposiciones de encuadre (Regla de Tercios, Nivel Digital).

🚀 FASE 5: Expansión Horizontal (Educación y Herramientas IA)
Análisis de tu Brainstorming
IA Auto-Tracking para contar o identificar (Visión Artificial):

Viabilidad: ¡Totalmente factible y muy potente! Google tiene una librería gratuita llamada ML Kit que funciona 100% offline (ideal para nuestra app).

Casos de uso: En la industria, el Lente apunta a un palet y la IA cuenta las cajas automáticamente. En educación, podría enfocar un circuito eléctrico y etiquetar resistencias y capacitores en tiempo real en la pantalla de los alumnos.

Picture-in-Picture (PiP) y Duplicar/Castear Pantalla:

Viabilidad: Android tiene soporte nativo para PiP. Castear a múltiples dispositivos encaja perfectamente con la meta de "Multidifusión (1-a-N)" que pusimos en la Fase 4.

Casos de uso: Un profesor (Lente) transmitiendo el experimento de química desde su celular hacia las tablets de 30 alumnos (Controles). El alumno puede minimizar el video del profesor (PiP) mientras toma notas en otra app.

Traductor en Tiempo Real (Subtítulos):

Viabilidad: Nuevamente, ML Kit ofrece traducción offline y reconocimiento de voz (Speech-to-Text).

Casos de uso: Un técnico en Alemania le habla a su celular en alemán, y el supervisor en Argentina (Control) lee los subtítulos en español superpuestos en el video en vivo.

Alerta Programada / Cambio de Imagen:

Viabilidad: Sencillo de implementar usando temporizadores en Kotlin (Coroutines o WorkManager).

Casos de uso: Mostrar un plano esquemático temporalmente sobre el video, o una alerta de "Cambio de turno" o "Peligro: Válvula abierta por más de 5 minutos".