@echo off
REM ============================================================================
REM Script para crear ejecutable Windows de Citofono Installer
REM Requiere: IExpress (incluido en Windows) o herramientas de terceros
REM ============================================================================

title Creador de Ejecutable - Citofono Installer

echo.
echo ============================================================================
echo                  CREADOR DE EJECUTABLE CITOFONO INSTALLER
echo ============================================================================
echo.

REM Verificar que todos los archivos necesarios están presentes
echo [INFO] Verificando archivos necesarios...

if not exist "install_citofono.bat" (
    echo [ERROR] Falta archivo: install_citofono.bat
    goto :error
)

if not exist "uninstall_citofono.bat" (
    echo [ERROR] Falta archivo: uninstall_citofono.bat
    goto :error
)

if not exist "config.ini" (
    echo [ERROR] Falta archivo: config.ini
    goto :error
)

if not exist "README_INSTALLER.md" (
    echo [ERROR] Falta archivo: README_INSTALLER.md
    goto :error
)

echo [OK] Todos los archivos necesarios están presentes.
echo.

REM Crear archivo SED para IExpress
echo [INFO] Creando configuración para IExpress...

set "SCRIPT_DIR=%~dp0"
set "OUTPUT_EXE=%SCRIPT_DIR%CitofonoInstaller.exe"

(
echo [Version]
echo Class=IEXPRESS
echo SEDVersion=3
echo [Options]
echo PackagePurpose=InstallApp
echo ShowInstallProgramWindow=1
echo HideExtractAnimation=0
echo UseLongFileName=1
echo InsideCompressed=0
echo CAB_FixedSize=0
echo CAB_ResvCodeSigning=0
echo RebootMode=N
echo InstallPrompt=%%InstallPrompt%%
echo DisplayLicense=%%DisplayLicense%%
echo FinishMessage=%%FinishMessage%%
echo TargetName=%%TargetName%%
echo FriendlyName=%%FriendlyName%%
echo AppLaunched=%%AppLaunched%%
echo PostInstallCmd=%%PostInstallCmd%%
echo AdminQuietInstCmd=%%AdminQuietInstCmd%%
echo UserQuietInstCmd=%%UserQuietInstCmd%%
echo FILE0="install_citofono.bat"
echo FILE1="uninstall_citofono.bat"
echo FILE2="config.ini"
echo FILE3="README_INSTALLER.md"
echo [Strings]
echo InstallPrompt=¿Desea instalar Citofono Installer en este equipo?
echo DisplayLicense=
echo FinishMessage=Citofono Installer se ha extraído correctamente. Ejecute install_citofono.bat para comenzar.
echo TargetName=%OUTPUT_EXE%
echo FriendlyName=Citofono Installer
echo AppLaunched=install_citofono.bat
echo PostInstallCmd=^<None^>
echo AdminQuietInstCmd=
echo UserQuietInstCmd=
) > citofono_installer.sed

echo [OK] Configuración creada: citofono_installer.sed
echo.

REM Intentar crear ejecutable con IExpress
echo [INFO] Intentando crear ejecutable con IExpress...
iexpress /N citofono_installer.sed

if exist "%OUTPUT_EXE%" (
    echo.
    echo [ÉXITO] Ejecutable creado exitosamente: CitofonoInstaller.exe
    echo.
    echo [INFO] El ejecutable incluye:
    echo        - install_citofono.bat (instalador principal)
    echo        - uninstall_citofono.bat (desinstalador)
    echo        - config.ini (configuración)
    echo        - README_INSTALLER.md (documentación)
    echo.
    echo [NOTA] Deberá colocar el archivo APK en la misma carpeta que el ejecutable.
    echo.
) else (
    echo [ERROR] No se pudo crear el ejecutable con IExpress.
    echo [INFO] Métodos alternativos disponibles:
    echo.
    echo 1. Usar herramientas de terceros:
    echo    - Bat To Exe Converter
    echo    - Advanced BAT to EXE Converter
    echo    - AutoIt Script to EXE Converter
    echo.
    echo 2. Usar PowerShell con PS2EXE:
    echo    - Install-Module ps2exe
    echo    - Invoke-ps2exe install_citofono.ps1 CitofonoInstaller.exe
    echo.
    echo 3. Crear instalador NSIS:
    echo    - Use Nullsoft Scriptable Install System
    echo    - Crear script .nsi personalizado
    echo.
)

REM Limpiar archivos temporales
if exist "citofono_installer.sed" del "citofono_installer.sed"

echo.
pause
exit /b 0

:error
echo.
echo [ERROR] No se pueden crear el ejecutable debido a archivos faltantes.
echo [INFO] Asegúrese de ejecutar este script desde la carpeta installer/
echo        que contiene todos los archivos necesarios.
echo.
pause
exit /b 1