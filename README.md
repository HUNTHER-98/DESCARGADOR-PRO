Video Downloader Android (yt-dlp GUI)

Una aplicación de Android moderna y funcional diseñada para descargar videos y audio de múltiples plataformas utilizando el poder de yt-dlp. 
Ofrece una interfaz intuitiva basada en Material Design 3 con gestión de colas y control de calidad.

🚀 Características

•Descarga Multi-plataforma: Soporte para cientos de sitios gracias a la integración con yt-dlp.
•Selector de Calidad: Elige el formato y resolución deseada antes de iniciar la descarga.
•Gestión de Cola: Visualiza y administra tus descargas activas en tiempo real.•Actualización Dinámica: Botón integrado para mantener el binario de yt-dlp siempre actualizado.
•Interfaz Moderna: Menú lateral (Navigation Drawer) y componentes Material 3 para una experiencia fluida.
•Progreso en Tiempo Real: Barra de estado y detalles de velocidad integrados en la UI.

 Tecnologías utilizadas
 
 •Lenguaje: Kotlin / Java (Android)•Interfaz: XML con Material Design 3
 •Motor de descarga: yt-dlp
 •Componentes clave: DrawerLayout, CardView, RecyclerView (para la cola), ProgressBar.

 📸 Vista Previa
 
 (Aquí puedes añadir una captura de pantalla de tu activity_main.xml cuando la app esté corriendo)

 📥 Instalación1
 
 .Clona este repositorio:

    git clone https://github.com/tu-usuario/tu-proyecto.git
   ```
2. Abre el proyecto en **Android Studio**.
3. Sincroniza el proyecto con los archivos de Gradle.
4. Asegúrate de tener conexión a internet para la descarga inicial de los binarios de yt-dlp y FFmpeg (si aplica).
5. Compila y ejecuta en tu dispositivo o emulador.

## 📖 Cómo usar

1. **Copia el enlace:** Copia la URL del video que deseas descargar.
2. **Pega y Configura:** Abre la app, pega el enlace en el campo de texto y selecciona la calidad preferida en el menú desplegable.
3. **Descarga:** Presiona el botón de descarga. Podrás ver el progreso en la sección de "Cola de descargas".
4. **Mantenimiento:** Usa el botón de **Actualizar** periódicamente para asegurar la compatibilidad con los sitios de video.

## 🛡️ Notas de Seguridad y Uso
Esta aplicación es una interfaz para la herramienta de código abierto yt-dlp. El usuario es responsable de asegurar que tiene los derechos para descargar el contenido según los términos de servicio de cada plataforma.

---

### Contribuir
Si quieres mejorar el diseño o añadir funcionalidades:
1. Haz un Fork del proyecto.
2. Crea una rama para tu mejora (`git checkout -b feature/MejoraIncreible`).
3. Haz un commit de tus cambios (`git commit -m 'feat: añadir nueva funcionalidad'`).
4. Haz un Push a la rama (`git push origin feature/MejoraIncreible`).
5. Abre un Pull Request.

---
**Desarrollado con ❤️ para la comunidad Open Source.**
---
