@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM Citofono Windows Installer
REM Automatiza la instalación de Citofono con permisos de Device Owner
REM ============================================================================

title Citofono - Instalador Automático

REM Variables de configuración
set "APP_PACKAGE=com.example.citofono"
set "DEVICE_ADMIN_RECEIVER=com.example.citofono/.MyDeviceAdminReceiver"
set "APK_NAME=app-debug.apk"
set "CONFIG_FILE=config.ini"

REM Colores para output (si está disponible)
set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

echo.
echo %BLUE%============================================================================%RESET%
echo %BLUE%                    CITOFONO - INSTALADOR AUTOMÁTICO                      %RESET%
echo %BLUE%============================================================================%RESET%
echo.

REM Cargar configuración si existe
if exist "%CONFIG_FILE%" (
    echo %YELLOW%[INFO]%RESET% Cargando configuración desde %CONFIG_FILE%...
    call :loadConfig
)

REM Verificar que ADB esté disponible
echo %YELLOW%[PASO 1/6]%RESET% Verificando disponibilidad de ADB...
adb version >nul 2>&1
if errorlevel 1 (
    echo %RED%[ERROR]%RESET% ADB no está disponible en el sistema.
    echo %RED%        %RESET% Por favor instale Android SDK Platform Tools y agregue ADB al PATH.
    echo %RED%        %RESET% Descarga: https://developer.android.com/studio/releases/platform-tools
    pause
    exit /b 1
)
echo %GREEN%[OK]%RESET% ADB encontrado y funcionando.

REM Verificar que el APK existe
echo %YELLOW%[PASO 2/6]%RESET% Verificando archivo APK...
if not exist "%APK_NAME%" (
    echo %RED%[ERROR]%RESET% No se encontró el archivo APK: %APK_NAME%
    echo %RED%        %RESET% Asegúrese de que el APK esté en la misma carpeta que este instalador.
    pause
    exit /b 1
)
echo %GREEN%[OK]%RESET% APK encontrado: %APK_NAME%

REM Detectar dispositivos conectados
echo %YELLOW%[PASO 3/6]%RESET% Detectando dispositivos Android conectados...
adb devices | findstr /R "device$" > temp_devices.txt
set /a device_count=0
for /f %%i in (temp_devices.txt) do set /a device_count+=1
del temp_devices.txt

if !device_count! equ 0 (
    echo %RED%[ERROR]%RESET% No se detectaron dispositivos Android conectados.
    echo %RED%        %RESET% Asegúrese de que:
    echo %RED%        %RESET% - El dispositivo esté conectado por USB
    echo %RED%        %RESET% - La depuración USB esté habilitada
    echo %RED%        %RESET% - Los drivers del dispositivo estén instalados
    echo %RED%        %RESET% - Haya autorizado la conexión ADB en el dispositivo
    pause
    exit /b 1
)

echo %GREEN%[OK]%RESET% Detectados !device_count! dispositivo(s) conectado(s).

REM Si hay múltiples dispositivos, mostrar lista
if !device_count! gtr 1 (
    echo.
    echo %YELLOW%[INFO]%RESET% Dispositivos detectados:
    adb devices
    echo.
    echo %YELLOW%[INFO]%RESET% El proceso se ejecutará en todos los dispositivos conectados.
    set /p continue="¿Desea continuar? (S/N): "
    if /i "!continue!" neq "S" (
        echo %YELLOW%[INFO]%RESET% Instalación cancelada por el usuario.
        pause
        exit /b 0
    )
)

REM Verificar estado del desarrollador y depuración USB
echo %YELLOW%[PASO 4/6]%RESET% Verificando configuración de dispositivos...
call :checkDeviceSettings
if errorlevel 1 exit /b 1

REM Limpiar instalaciones previas
echo %YELLOW%[PASO 5/6]%RESET% Limpiando instalaciones previas...
call :cleanPreviousInstallation

REM Instalar y configurar
echo %YELLOW%[PASO 6/6]%RESET% Instalando Citofono con permisos de Device Owner...
call :installAndSetupDeviceOwner
if errorlevel 1 exit /b 1

echo.
echo %GREEN%============================================================================%RESET%
echo %GREEN%                        INSTALACIÓN COMPLETADA                           %RESET%
echo %GREEN%============================================================================%RESET%
echo.
echo %GREEN%[ÉXITO]%RESET% Citofono ha sido instalado correctamente con permisos de Device Owner.
echo %GREEN%        %RESET% La aplicación debería iniciar automáticamente en modo Kiosk.
echo.
echo %YELLOW%[INFO]%RESET% Para desinstalar o quitar permisos, ejecute: uninstall_citofono.bat
echo.
pause
exit /b 0

REM ============================================================================
REM FUNCIONES
REM ============================================================================

:loadConfig
REM Función para cargar configuración desde archivo INI
for /f "usebackq tokens=1,2 delims==" %%a in ("%CONFIG_FILE%") do (
    if "%%a"=="APP_PACKAGE" set "APP_PACKAGE=%%b"
    if "%%a"=="DEVICE_ADMIN_RECEIVER" set "DEVICE_ADMIN_RECEIVER=%%b"
    if "%%a"=="APK_NAME" set "APK_NAME=%%b"
)
goto :eof

:checkDeviceSettings
REM Verificar configuraciones críticas del dispositivo
echo %YELLOW%[INFO]%RESET% Verificando configuración de desarrollador en dispositivos...

for /f "tokens=1" %%d in ('adb devices ^| findstr /R "device$"') do (
    echo %YELLOW%[INFO]%RESET% Verificando dispositivo: %%d
    
    REM Verificar si la depuración USB está habilitada
    adb -s %%d shell settings get global adb_enabled 2>nul | findstr "1" >nul
    if errorlevel 1 (
        echo %RED%[ERROR]%RESET% La depuración USB no está habilitada en el dispositivo %%d
        echo %RED%        %RESET% Habilite "Depuración USB" en Configuración ^> Opciones de desarrollador
        exit /b 1
    )
    
    REM Verificar versión de Android (mínimo API 21 para Device Owner)
    for /f "tokens=*" %%v in ('adb -s %%d shell getprop ro.build.version.sdk 2^>nul') do (
        if %%v lss 21 (
            echo %RED%[ERROR]%RESET% El dispositivo %%d tiene Android API %%v (Android 5.0+ requerido)
            exit /b 1
        )
        echo %GREEN%[OK]%RESET% Dispositivo %%d - Android API %%v compatible
    )
)
goto :eof

:cleanPreviousInstallation
REM Limpiar instalaciones previas de la aplicación
echo %YELLOW%[INFO]%RESET% Verificando instalaciones previas...

for /f "tokens=1" %%d in ('adb devices ^| findstr /R "device$"') do (
    echo %YELLOW%[INFO]%RESET% Limpiando dispositivo: %%d
    
    REM Verificar si la app está instalada
    adb -s %%d shell pm list packages | findstr "%APP_PACKAGE%" >nul
    if not errorlevel 1 (
        echo %YELLOW%[INFO]%RESET% Aplicación encontrada, removiendo permisos de Device Owner...
        
        REM Intentar remover Device Owner (puede fallar si no está configurado)
        adb -s %%d shell dpm remove-active-admin "%DEVICE_ADMIN_RECEIVER%" 2>nul
        
        echo %YELLOW%[INFO]%RESET% Desinstalando aplicación previa...
        adb -s %%d uninstall "%APP_PACKAGE%" >nul 2>&1
    )
    
    REM Desinstalar por seguridad (incluso si no está visible)
    echo %YELLOW%[INFO]%RESET% Ejecutando desinstalación de seguridad...
    adb -s %%d uninstall "%APP_PACKAGE%" >nul 2>&1
    
    echo %GREEN%[OK]%RESET% Limpieza completada para dispositivo %%d
)
goto :eof

:installAndSetupDeviceOwner
REM Instalar APK y configurar como Device Owner
echo %YELLOW%[INFO]%RESET% Iniciando instalación en dispositivos...

for /f "tokens=1" %%d in ('adb devices ^| findstr /R "device$"') do (
    echo.
    echo %YELLOW%[INFO]%RESET% Procesando dispositivo: %%d
    
    REM Instalar APK
    echo %YELLOW%[INFO]%RESET% Instalando APK...
    adb -s %%d install "%APK_NAME%"
    if errorlevel 1 (
        echo %RED%[ERROR]%RESET% Error al instalar APK en dispositivo %%d
        exit /b 1
    )
    echo %GREEN%[OK]%RESET% APK instalado correctamente
    
    REM Breve pausa para que el sistema registre la instalación
    timeout /t 2 /nobreak >nul
    
    REM Configurar como Device Owner
    echo %YELLOW%[INFO]%RESET% Configurando permisos de Device Owner...
    adb -s %%d shell dpm set-device-owner "%DEVICE_ADMIN_RECEIVER%"
    if errorlevel 1 (
        echo %RED%[ERROR]%RESET% Error al configurar Device Owner en dispositivo %%d
        echo %RED%        %RESET% Posibles causas:
        echo %RED%        %RESET% - Ya existe otro Device Owner en el dispositivo
        echo %RED%        %RESET% - Hay cuentas de usuario configuradas (Google, etc.)
        echo %RED%        %RESET% - El dispositivo no cumple los requisitos para Device Owner
        echo %RED%        %RESET% 
        echo %RED%        %RESET% Solución: Realizar factory reset y ejecutar antes de configurar cuentas
        exit /b 1
    )
    echo %GREEN%[OK]%RESET% Device Owner configurado correctamente
    
    REM Verificar configuración
    echo %YELLOW%[INFO]%RESET% Verificando configuración de Device Owner...
    adb -s %%d shell dpm list-owners | findstr "%APP_PACKAGE%" >nul
    if errorlevel 1 (
        echo %RED%[WARNING]%RESET% No se pudo verificar la configuración de Device Owner
    ) else (
        echo %GREEN%[OK]%RESET% Device Owner verificado correctamente
    )
    
    REM Iniciar la aplicación
    echo %YELLOW%[INFO]%RESET% Iniciando aplicación...
    adb -s %%d shell monkey -p "%APP_PACKAGE%" -c android.intent.category.LAUNCHER 1 >nul 2>&1
    
    echo %GREEN%[COMPLETADO]%RESET% Dispositivo %%d configurado exitosamente
)

goto :eof

REM ============================================================================
REM FIN DEL SCRIPT
REM ============================================================================