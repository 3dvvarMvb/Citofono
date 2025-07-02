@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM Citofono Windows Uninstaller
REM Remueve Citofono y sus permisos de Device Owner
REM ============================================================================

title Citofono - Desinstalador

REM Variables de configuración
set "APP_PACKAGE=com.example.citofono"
set "DEVICE_ADMIN_RECEIVER=com.example.citofono/.MyDeviceAdminReceiver"
set "CONFIG_FILE=config.ini"

REM Colores para output
set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

echo.
echo %BLUE%============================================================================%RESET%
echo %BLUE%                    CITOFONO - DESINSTALADOR                              %RESET%
echo %BLUE%============================================================================%RESET%
echo.

REM Cargar configuración si existe
if exist "%CONFIG_FILE%" (
    echo %YELLOW%[INFO]%RESET% Cargando configuración desde %CONFIG_FILE%...
    call :loadConfig
)

REM Verificar que ADB esté disponible
echo %YELLOW%[PASO 1/3]%RESET% Verificando disponibilidad de ADB...
adb version >nul 2>&1
if errorlevel 1 (
    echo %RED%[ERROR]%RESET% ADB no está disponible en el sistema.
    echo %RED%        %RESET% Por favor instale Android SDK Platform Tools.
    pause
    exit /b 1
)
echo %GREEN%[OK]%RESET% ADB encontrado y funcionando.

REM Detectar dispositivos conectados
echo %YELLOW%[PASO 2/3]%RESET% Detectando dispositivos Android conectados...
adb devices | findstr /R "device$" > temp_devices.txt
set /a device_count=0
for /f %%i in (temp_devices.txt) do set /a device_count+=1
del temp_devices.txt

if !device_count! equ 0 (
    echo %RED%[ERROR]%RESET% No se detectaron dispositivos Android conectados.
    pause
    exit /b 1
)

echo %GREEN%[OK]%RESET% Detectados !device_count! dispositivo(s) conectado(s).

REM Confirmar desinstalación
echo.
echo %YELLOW%[ADVERTENCIA]%RESET% Esta acción removerá completamente Citofono y sus permisos.
set /p continue="¿Está seguro de que desea continuar? (S/N): "
if /i "!continue!" neq "S" (
    echo %YELLOW%[INFO]%RESET% Desinstalación cancelada por el usuario.
    pause
    exit /b 0
)

REM Remover aplicación y permisos
echo %YELLOW%[PASO 3/3]%RESET% Removiendo Citofono y permisos de Device Owner...
call :uninstallApplication

echo.
echo %GREEN%============================================================================%RESET%
echo %GREEN%                      DESINSTALACIÓN COMPLETADA                          %RESET%
echo %GREEN%============================================================================%RESET%
echo.
echo %GREEN%[ÉXITO]%RESET% Citofono ha sido removido completamente de todos los dispositivos.
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
)
goto :eof

:uninstallApplication
REM Remover aplicación y permisos de todos los dispositivos
for /f "tokens=1" %%d in ('adb devices ^| findstr /R "device$"') do (
    echo.
    echo %YELLOW%[INFO]%RESET% Procesando dispositivo: %%d
    
    REM Verificar si la app está instalada
    adb -s %%d shell pm list packages | findstr "%APP_PACKAGE%" >nul
    if errorlevel 1 (
        echo %YELLOW%[INFO]%RESET% Citofono no está instalado en dispositivo %%d
    ) else (
        echo %YELLOW%[INFO]%RESET% Citofono encontrado, procediendo con la desinstalación...
        
        REM Detener la aplicación si está corriendo
        echo %YELLOW%[INFO]%RESET% Deteniendo aplicación...
        adb -s %%d shell am force-stop "%APP_PACKAGE%" 2>nul
        
        REM Salir del modo Kiosk si está activo
        echo %YELLOW%[INFO]%RESET% Saliendo del modo Kiosk...
        adb -s %%d shell am task lock stop 2>nul
        
        REM Remover permisos de Device Owner
        echo %YELLOW%[INFO]%RESET% Removiendo permisos de Device Owner...
        adb -s %%d shell dpm remove-active-admin "%DEVICE_ADMIN_RECEIVER%" 2>nul
        
        REM Remover como Device Owner si es necesario
        adb -s %%d shell dpm set-device-owner "" 2>nul
        
        REM Desinstalar aplicación
        echo %YELLOW%[INFO]%RESET% Desinstalando aplicación...
        adb -s %%d uninstall "%APP_PACKAGE%"
        if errorlevel 1 (
            echo %RED%[WARNING]%RESET% Error al desinstalar en dispositivo %%d
        ) else (
            echo %GREEN%[OK]%RESET% Aplicación desinstalada correctamente
        )
    )
    
    REM Verificar que la aplicación fue removida
    adb -s %%d shell pm list packages | findstr "%APP_PACKAGE%" >nul
    if errorlevel 1 (
        echo %GREEN%[OK]%RESET% Dispositivo %%d limpio
    ) else (
        echo %RED%[WARNING]%RESET% La aplicación aún está presente en dispositivo %%d
    )
)

goto :eof

REM ============================================================================
REM FIN DEL SCRIPT
REM ============================================================================