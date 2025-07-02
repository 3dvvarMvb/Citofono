# Citofono

## Descripción
Citofono es un proyecto desarrollado en Kotlin que tiene como objetivo mitigar los problemas de comunicacion entre conserjes y administradores con su entorno de lugar residencial.

## Características
- Kiosk mode integrado
- Lectura y escritura de archivos csv, xlsx
- Interfaz intuitiva
- **Instalador automático Windows** para configuración Device Owner
- Soporte para múltiples dispositivos
- Manejo automático de permisos y configuraciones 

## Requisitos del Sistema
- "Android 5.0 o superior"

## Instalación

### Opción 1: Instalador Automático Windows (Recomendado)
1. Clona este repositorio: `git clone https://github.com/3dvvarMvb/Citofono.git`
2. Genera el APK: `./gradlew assembleDebug`
3. Copia el APK generado a la carpeta `installer/`
4. Ejecuta `installer/install_citofono.bat` como Administrador
5. Sigue las instrucciones del instalador automático

**Ver documentación completa**: [installer/README_INSTALLER.md](installer/README_INSTALLER.md)

### Opción 2: Instalación Manual
1. Clona este repositorio: `git clone https://github.com/3dvvarMvb/Citofono.git`
2. Abre el proyecto en tu IDE preferido (recomendado: Android Studio).
3. Compila y ejecuta la aplicación en un dispositivo o emulador.
4. Abre la consola de comandos y ejecuta el siguiente comando adb: 'adb shell dpm set-device-owner com.example.citofono/.MyDeviceAdminReceiver'


## Contacto
Para cualquier consulta, puedes contactarme a través de eduarmaxi.2003@gmail.com o abrir un issue en el repositorio.

---

¡Gracias por usar Citofono!
