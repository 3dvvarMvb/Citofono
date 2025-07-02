# Instrucciones para Crear el Ejecutable Windows (.exe)

## Método 1: IExpress (Incluido en Windows)

1. Ejecute `create_executable.bat` en la carpeta installer/
2. El script creará automáticamente la configuración para IExpress
3. Se generará `CitofonoInstaller.exe` en la misma carpeta

## Método 2: Bat To Exe Converter (Recomendado)

### Descargar herramienta:
- Sitio web: http://www.f2ko.de/en/b2e.php
- Descargue "Bat To Exe Converter"

### Pasos:
1. Abra Bat To Exe Converter
2. Seleccione `install_citofono.bat` como archivo fuente
3. Configure opciones:
   - **Include files**: Agregue todos los archivos del installer
   - **Icon**: Use un icono personalizado si lo desea
   - **Version info**: Complete información de la aplicación
   - **Options**: Marque "Include all files from the source directory"
4. Genere el ejecutable

## Método 3: PowerShell con PS2EXE

### Instalar PS2EXE:
```powershell
Install-Module ps2exe -Scope CurrentUser
```

### Crear ejecutable:
```powershell
Invoke-ps2exe .\install_citofono.ps1 .\CitofonoInstaller.exe -iconFile icon.ico
```

## Método 4: NSIS (Nullsoft Scriptable Install System)

### Instalar NSIS:
- Descargar desde: https://nsis.sourceforge.io/Download

### Crear script NSIS:
```nsis
!define APPNAME "Citofono Installer"
!define VERSION "1.0"

Name "${APPNAME}"
OutFile "CitofonoInstaller.exe"
InstallDir "$DESKTOP\CitofonoInstaller"

Section "MainSection" SEC01
    SetOutPath "$INSTDIR"
    File "install_citofono.bat"
    File "uninstall_citofono.bat"
    File "config.ini"
    File "README_INSTALLER.md"
    
    CreateShortCut "$DESKTOP\Citofono Installer.lnk" "$INSTDIR\install_citofono.bat"
    
    ExecWait '"$INSTDIR\install_citofono.bat"'
SectionEnd
```

## Método 5: AutoIt (Avanzado)

### Instalar AutoIt:
- Descargar desde: https://www.autoitscript.com/

### Script AutoIt básico:
```autoit
#include <Constants.au3>

; Extraer archivos embebidos
FileInstall("install_citofono.bat", @TempDir & "\install_citofono.bat")
FileInstall("config.ini", @TempDir & "\config.ini")

; Ejecutar instalador
RunWait(@TempDir & "\install_citofono.bat")

; Limpiar archivos temporales
FileDelete(@TempDir & "\install_citofono.bat")
FileDelete(@TempDir & "\config.ini")
```

## Configuración Recomendada para el Ejecutable

### Información del archivo:
- **Nombre**: CitofonoInstaller.exe
- **Descripción**: Instalador automático para Citofono con Device Owner
- **Versión**: 1.0.0.0
- **Copyright**: © 2024 Citofono Project
- **Compañía**: Su empresa/organización

### Opciones de compilación:
- **Incluir archivos**: Todos los archivos de la carpeta installer/
- **Directorio de trabajo**: Mismo directorio del ejecutable
- **Mostrar consola**: Sí (para ver el progreso de instalación)
- **Ejecutar como administrador**: Recomendado

### Archivos a incluir en el ejecutable:
```
installer/
├── install_citofono.bat     ✓ Incluir
├── uninstall_citofono.bat   ✓ Incluir  
├── config.ini              ✓ Incluir
├── README_INSTALLER.md     ✓ Incluir
├── install_citofono.ps1    ✓ Incluir (opcional)
└── app-debug.apk           ✗ NO incluir (debe estar separado)
```

## Pruebas del Ejecutable

### Antes de distribuir:
1. Teste en una máquina limpia de Windows
2. Verifique que ADB está disponible o incluya platform-tools
3. Teste con diferentes dispositivos Android
4. Verifique el manejo de errores

### Casos de prueba:
- [ ] Ejecutable se extrae correctamente
- [ ] Script de instalación se ejecuta
- [ ] Detecta dispositivos Android
- [ ] Maneja errores de ADB
- [ ] Instala APK correctamente
- [ ] Configura Device Owner
- [ ] Desinstalador funciona

## Distribución

### Archivos a distribuir:
```
CitofonoInstaller/
├── CitofonoInstaller.exe    # Ejecutable principal
├── app-debug.apk           # APK de Citofono
└── Instrucciones.txt       # Instrucciones básicas
```

### Instrucciones básicas para el usuario:
1. Coloque el APK en la misma carpeta que CitofonoInstaller.exe
2. Ejecute CitofonoInstaller.exe como Administrador
3. Siga las instrucciones en pantalla
4. Para desinstalar, extraiga y ejecute uninstall_citofono.bat

## Consideraciones de Seguridad

- El ejecutable debe ser firmado digitalmente si es posible
- Incluir checksums para verificar integridad del APK
- Documentar todos los permisos requeridos
- Proporcionar código fuente para auditoría

## Soporte y Mantenimiento

- Versionar el ejecutable con cada actualización del APK
- Mantener documentación actualizada
- Proporcionar canal de soporte para usuarios
- Crear sistema de logs para diagnosticar problemas