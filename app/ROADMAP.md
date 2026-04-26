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

[ ] Aviso Remoto de Captura: Confirmación visual en la pantalla del "Control" cuando el "Lente" guarda la foto exitosamente.

[ ] Modo "Pantalla Oscura" (Lente): Ahorro extremo de batería atenuando el brillo del sistema y bloqueando toques accidentales para transmisión continua.

[ ] Selector de Calidad UI: Botón funcional en el Drawer de Configuración para alternar SD/HD antes o durante la transmisión.

[ ] Manejo de Desconexiones: UI clara cuando el Lente o el Control pierden señal inesperadamente.

🔵 FASE 3: "Killer Features" B2B (Diferenciadores de Mercado)

Objetivo: Implementar las herramientas visuales que justifican la monetización comercial.

[ ] Puntero AR / Telestration: Permitir al "Control" tocar su pantalla y que aparezca un marcador rojo en la vista en vivo del "Lente".

[ ] Control de Telemetría: Zoom in/out y control de enfoque (Tap-to-focus) operados remotamente desde el dispositivo Control.

[ ] Intercomunicador de Voz: Canal STREAM paralelo de Nearby para audio bidireccional sin depender de llamadas GSM/WhatsApp.

🟣 FASE 4: Escalabilidad Arquitectónica y Monetización

Objetivo: Rediseño del motor interno para dar el salto a nivel Enterprise.

[ ] Migración a H.265 (MediaCodec): Reemplazar MJPEG por codificación de hardware para reducir drásticamente el peso de red y mejorar los FPS.

[ ] Modo "Centro de Comando" (N-a-1): Un Control monitoreando múltiples Lentes simultáneamente.

[ ] Multidifusión (1-a-N): Un Lente transmitiendo a múltiples técnicos simultáneamente.

[ ] Marca de Agua + Evidencia (Metadatos): Imprimir coordenadas GPS, fecha y hora directamente en los píxeles de la foto HD capturada.

Última actualización: Abril 2026