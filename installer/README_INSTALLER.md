# Citofono - Instalador Windows

Este instalador automatiza el proceso de instalación de la aplicación Citofono con permisos de Device Owner en dispositivos Android.

## 📋 Requisitos Previos

### Sistema Windows
- Windows 7 o superior
- Android SDK Platform Tools (ADB) instalado y configurado en PATH
- Drivers USB del dispositivo Android instalados

### Dispositivo Android
- Android 5.0 (API 21) o superior
- Modo Desarrollador habilitado
- Depuración USB habilitada
- **IMPORTANTE:** El dispositivo debe estar en estado de fábrica (sin cuentas configuradas) para permitir Device Owner

## 📁 Archivos del Instalador

```
installer/
├── install_citofono.bat     # Script principal de instalación
├── uninstall_citofono.bat   # Script de desinstalación
├── config.ini              # Archivo de configuración
├── README_INSTALLER.md     # Esta documentación
└── app-debug.apk           # APK de Citofono (debe colocarse aquí)
```

## 🚀 Instalación

### Paso 1: Preparar el entorno
1. Descargue e instale Android SDK Platform Tools desde:
   https://developer.android.com/studio/releases/platform-tools
2. Agregue la carpeta de platform-tools al PATH del sistema
3. Verifique que ADB funciona ejecutando `adb version` en cmd

### Paso 2: Preparar el dispositivo
1. **Factory Reset:** Realice un restablecimiento de fábrica del dispositivo
2. **NO configure cuentas:** No agregue cuentas de Google u otras durante la configuración inicial
3. **Habilitar Desarrollador:**
   - Vaya a Configuración > Acerca del teléfono
   - Toque "Número de compilación" 7 veces
4. **Habilitar Depuración USB:**
   - Vaya a Configuración > Opciones de desarrollador
   - Active "Depuración USB"
5. **Conectar dispositivo:** Conecte por USB y autorice la conexión ADB

### Paso 3: Ejecutar instalación
1. Coloque el archivo APK de Citofono en la carpeta `installer/`
2. Asegúrese de que el nombre del APK coincida con el configurado (por defecto: `app-debug.apk`)
3. Ejecute `install_citofono.bat` como Administrador
4. Siga las instrucciones en pantalla

## ⚙️ Configuración

Puede personalizar el instalador editando el archivo `config.ini`:

```ini
# Información de la aplicación
APP_PACKAGE=com.example.citofono
DEVICE_ADMIN_RECEIVER=com.example.citofono/.MyDeviceAdminReceiver

# Nombre del archivo APK
APK_NAME=app-debug.apk
```

## 🔧 Funcionalidades del Instalador

### Verificaciones automáticas:
- ✅ Disponibilidad de ADB
- ✅ Presencia del archivo APK
- ✅ Dispositivos Android conectados
- ✅ Estado de depuración USB
- ✅ Versión de Android compatible

### Proceso de instalación:
1. **Limpieza previa:** Remueve instalaciones anteriores y permisos
2. **Instalación:** Instala el APK mediante ADB
3. **Configuración:** Establece permisos de Device Owner
4. **Verificación:** Confirma que la configuración es correcta
5. **Inicio:** Lanza la aplicación automáticamente

### Soporte multi-dispositivo:
- Detecta y procesa múltiples dispositivos conectados
- Muestra progreso individual por dispositivo
- Manejo de errores específico por dispositivo

## 🗑️ Desinstalación

Para remover completamente Citofono:

1. Ejecute `uninstall_citofono.bat`
2. Confirme la desinstalación cuando se le solicite
3. El script removerá automáticamente:
   - Permisos de Device Owner
   - La aplicación Citofono
   - Configuraciones relacionadas

## ❌ Solución de Problemas

### Error: "ADB no está disponible"
**Solución:** 
- Instale Android SDK Platform Tools
- Agregue la carpeta al PATH del sistema
- Reinicie el cmd y verifique con `adb version`

### Error: "No se detectaron dispositivos"
**Solución:**
- Verifique que el dispositivo esté conectado por USB
- Autorice la conexión ADB en el dispositivo
- Instale los drivers USB del dispositivo
- Pruebe ejecutar `adb devices` manualmente

### Error: "Error al configurar Device Owner"
**Posibles causas:**
- Ya existe otro Device Owner en el dispositivo
- Hay cuentas de usuario configuradas (Google, etc.)
- El dispositivo no está en estado de fábrica

**Solución:**
- Realice un factory reset completo
- NO configure cuentas durante la configuración inicial
- Ejecute el instalador inmediatamente después del reset

### Error: "La aplicación no inicia en modo Kiosk"
**Verificación:**
- Confirme que los permisos de Device Owner están activos
- Ejecute: `adb shell dpm list-owners`
- Debe mostrar: `com.example.citofono/.MyDeviceAdminReceiver`

### Error: "Permiso denegado para realizar llamadas"
**Solución:**
- Este es un comportamiento normal en la primera ejecución
- Otorgue los permisos cuando la aplicación los solicite
- Los permisos se recordarán para futuras ejecuciones

## 📱 Uso de la Aplicación

Una vez instalada correctamente:

1. **Modo Kiosk:** La aplicación iniciará automáticamente en modo Kiosk
2. **Búsqueda de contactos:** Use la barra de búsqueda para encontrar departamentos
3. **Realizar llamadas:** Toque un contacto para realizar la llamada
4. **Administración:** Use el botón de configuración para acceder al panel de administración
5. **Salir de Kiosk:** Solo disponible desde el panel de administración

## 🔒 Seguridad

### Permisos otorgados:
- **Device Owner:** Control total sobre el dispositivo
- **Kiosk Mode:** Restricción de acceso a otras aplicaciones
- **Phone:** Capacidad de realizar llamadas telefónicas
- **Contacts:** Lectura y escritura de contactos

### Recomendaciones:
- Use solo en dispositivos dedicados para esta función
- Mantenga copias de seguridad de los contactos
- Documente la configuración para futuras referencias

## 🆘 Soporte

Para problemas adicionales:
1. Verifique los logs de ADB: `adb logcat | findstr citofono`
2. Ejecute el instalador en modo verbose (si está disponible)
3. Consulte la documentación oficial de Android Device Owner
4. Contacte al desarrollador con detalles específicos del error

## 📝 Notas Técnicas

### Comandos ADB utilizados:
```bash
adb devices                                    # Listar dispositivos
adb install app-debug.apk                     # Instalar APK
adb shell dpm set-device-owner <receiver>     # Configurar Device Owner
adb shell dpm remove-active-admin <receiver>  # Remover permisos
adb uninstall <package>                       # Desinstalar aplicación
```

### Archivos generados:
- **Ninguno:** El instalador no genera archivos adicionales en el sistema
- **Logs:** Los logs de ADB pueden consultarse con `adb logcat`

---

**Versión:** 1.0  
**Última actualización:** 2024  
**Compatibilidad:** Windows 7+, Android 5.0+