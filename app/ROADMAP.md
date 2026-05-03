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

✅ Evidencia Forense (GPS + Metadatos): Marca de agua con coordenadas de alta precisión (FusedLocationProvider) y fecha/hora incrustadas directamente en los píxeles de la captura HD. (Completado)

[ ] Migración H.265 (Paso 1) - Motor Zero-Copy: Creación del VideoEncoderManager para conectar el SurfaceProvider de CameraX directo al hardware (MediaCodec), configurando baja latencia e I-Frames.

[ ] Migración H.265 (Paso 2) - Transporte P2P y Fallback: Empaquetar la salida binaria (NAL Units / Annex B) y enviarla por Nearby Connections, incluyendo el rescate automático a H.264 si el Control no soporta HEVC.

[ ] Migración H.265 (Paso 3) - Control Heurístico (BWE): Implementar la lectura de velocidad de red y la adaptación dinámica de Bitrate al vuelo para evitar el estrangulamiento térmico de los equipos.

[ ] Lector Inteligente (OCR/Barcode): Extracción de números de serie y códigos de barras en el Control vía ML Kit usando el Lente remoto.

[ ] Modo Enterprise (Multidifusión): Soporte N-a-1 y 1-a-N (Un Control para múltiples Lentes, o un Lente transmitiendo a múltiples controles).

[ ] Captura RAW y Grillas: Soporte para formato DNG y superposiciones de encuadre en pantalla (Regla de Tercios, Nivel Digital).

🚀 FASE 5: Expansión Horizontal (IA y Herramientas Avanzadas)
Objetivo: Integración de Visión Artificial offline y herramientas de colaboración masiva.

[ ] IA Auto-Tracking (Visión Artificial): Conteo automático de objetos (cajas, pallets) o identificación de componentes (resistencias, capacitores) en tiempo real usando modelos offline de ML Kit.

[ ] Picture-in-Picture (PiP) y Duplicar Pantalla: Soporte nativo de Android para minimizar el video en vivo, permitiendo al usuario revisar manuales o tomar notas en paralelo.

[ ] Traductor en Tiempo Real (Subtítulos): Reconocimiento de voz (Speech-to-Text) y traducción offline para mostrar indicaciones de voz como subtítulos sobre el video.

[ ] Alertas Programadas / Overlay Dinámico: Sistema de temporizadores (Coroutines/WorkManager) para mostrar advertencias de seguridad o esquemas técnicos superpuestos en la vista del Lente.