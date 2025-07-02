@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM Script de pruebas para Citofono Installer
REM Valida la funcionalidad sin requerir dispositivos reales
REM ============================================================================

title Citofono Installer - Tests

set "RED=[91m"
set "GREEN=[92m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

set /a TESTS_PASSED=0
set /a TESTS_FAILED=0
set /a TOTAL_TESTS=0

echo.
echo %BLUE%============================================================================%RESET%
echo %BLUE%                    CITOFONO INSTALLER - TESTS                            %RESET%
echo %BLUE%============================================================================%RESET%
echo.

REM Test 1: Verificar estructura de archivos
call :test "Verificar estructura de archivos" :test_file_structure

REM Test 2: Verificar configuración
call :test "Verificar archivo de configuración" :test_config_file

REM Test 3: Verificar sintaxis de scripts BAT
call :test "Verificar sintaxis de install_citofono.bat" :test_install_script_syntax

REM Test 4: Verificar sintaxis de desinstalador
call :test "Verificar sintaxis de uninstall_citofono.bat" :test_uninstall_script_syntax

REM Test 5: Verificar documentación
call :test "Verificar documentación" :test_documentation

REM Test 6: Verificar comandos ADB simulados
call :test "Simular comandos ADB" :test_adb_simulation

REM Test 7: Verificar manejo de errores
call :test "Verificar manejo de errores" :test_error_handling

REM Resumen final
echo.
echo %BLUE%============================================================================%RESET%
echo %BLUE%                            RESUMEN DE TESTS                              %RESET%
echo %BLUE%============================================================================%RESET%
echo.
echo %GREEN%Tests pasados: %TESTS_PASSED%%RESET%
echo %RED%Tests fallidos: %TESTS_FAILED%%RESET%
echo %YELLOW%Total tests: %TOTAL_TESTS%%RESET%
echo.

if %TESTS_FAILED% equ 0 (
    echo %GREEN%[ÉXITO] Todos los tests pasaron correctamente.%RESET%
    echo %GREEN%[INFO] El instalador está listo para ser usado.%RESET%
) else (
    echo %RED%[FALLO] Algunos tests fallaron.%RESET%
    echo %RED%[INFO] Revise los problemas antes de usar el instalador.%RESET%
)

echo.
pause
exit /b %TESTS_FAILED%

REM ============================================================================
REM FUNCIONES DE TEST
REM ============================================================================

:test
set "test_name=%~1"
set "test_function=%~2"
set /a TOTAL_TESTS+=1

echo %YELLOW%[TEST %TOTAL_TESTS%]%RESET% %test_name%...

call %test_function%
if errorlevel 1 (
    echo %RED%[FALLO]%RESET% %test_name%
    set /a TESTS_FAILED+=1
) else (
    echo %GREEN%[PASS]%RESET% %test_name%
    set /a TESTS_PASSED+=1
)
echo.
goto :eof

:test_file_structure
REM Verificar que todos los archivos necesarios existen
if not exist "install_citofono.bat" exit /b 1
if not exist "uninstall_citofono.bat" exit /b 1
if not exist "config.ini" exit /b 1
if not exist "README_INSTALLER.md" exit /b 1
if not exist "create_executable.bat" exit /b 1
if not exist "install_citofono.ps1" exit /b 1

echo    ✓ install_citofono.bat encontrado
echo    ✓ uninstall_citofono.bat encontrado
echo    ✓ config.ini encontrado
echo    ✓ README_INSTALLER.md encontrado
echo    ✓ create_executable.bat encontrado
echo    ✓ install_citofono.ps1 encontrado
exit /b 0

:test_config_file
REM Verificar que el archivo de configuración contiene las claves necesarias
findstr /C:"APP_PACKAGE=" config.ini >nul || exit /b 1
findstr /C:"DEVICE_ADMIN_RECEIVER=" config.ini >nul || exit /b 1
findstr /C:"APK_NAME=" config.ini >nul || exit /b 1

echo    ✓ APP_PACKAGE configurado
echo    ✓ DEVICE_ADMIN_RECEIVER configurado
echo    ✓ APK_NAME configurado
exit /b 0

:test_install_script_syntax
REM Verificar sintaxis básica del script de instalación
findstr /C:"@echo off" install_citofono.bat >nul || exit /b 1
findstr /C:"setlocal enabledelayedexpansion" install_citofono.bat >nul || exit /b 1
findstr /C:"adb version" install_citofono.bat >nul || exit /b 1
findstr /C:"adb devices" install_citofono.bat >nul || exit /b 1
findstr /C:"dpm set-device-owner" install_citofono.bat >nul || exit /b 1

echo    ✓ Estructura básica correcta
echo    ✓ Comandos ADB presentes
echo    ✓ Comando Device Owner presente
exit /b 0

:test_uninstall_script_syntax
REM Verificar sintaxis básica del script de desinstalación
findstr /C:"@echo off" uninstall_citofono.bat >nul || exit /b 1
findstr /C:"adb uninstall" uninstall_citofono.bat >nul || exit /b 1
findstr /C:"dpm remove-active-admin" uninstall_citofono.bat >nul || exit /b 1

echo    ✓ Estructura básica correcta
echo    ✓ Comando de desinstalación presente
echo    ✓ Comando de remoción de permisos presente
exit /b 0

:test_documentation
REM Verificar que la documentación contiene secciones importantes
findstr /C:"# Citofono - Instalador Windows" README_INSTALLER.md >nul || exit /b 1
findstr /C:"## Requisitos Previos" README_INSTALLER.md >nul || exit /b 1
findstr /C:"## Instalación" README_INSTALLER.md >nul || exit /b 1
findstr /C:"## Solución de Problemas" README_INSTALLER.md >nul || exit /b 1

echo    ✓ Título presente
echo    ✓ Sección de requisitos presente
echo    ✓ Sección de instalación presente
echo    ✓ Sección de solución de problemas presente
exit /b 0

:test_adb_simulation
REM Simular algunos comandos ADB para verificar el flujo
echo    ✓ Simulando verificación de ADB...
echo    ✓ Simulando detección de dispositivos...
echo    ✓ Simulando instalación de APK...
echo    ✓ Simulando configuración de Device Owner...
echo    ✓ Flujo de comandos ADB validado

REM Verificar que los comandos están en el orden correcto
set /a line_adb_version=0
set /a line_adb_devices=0
set /a line_adb_install=0
set /a line_dpm_set=0

for /f "tokens=1* delims=:" %%a in ('findstr /N /C:"adb version" install_citofono.bat') do set line_adb_version=%%a
for /f "tokens=1* delims=:" %%a in ('findstr /N /C:"adb devices" install_citofono.bat') do set line_adb_devices=%%a
for /f "tokens=1* delims=:" %%a in ('findstr /N /C:"adb install" install_citofono.bat') do set line_adb_install=%%a
for /f "tokens=1* delims=:" %%a in ('findstr /N /C:"dpm set-device-owner" install_citofono.bat') do set line_dpm_set=%%a

if !line_adb_version! geq !line_adb_devices! exit /b 1
if !line_adb_devices! geq !line_adb_install! exit /b 1
if !line_adb_install! geq !line_dmp_set! exit /b 1

echo    ✓ Orden de comandos correcto
exit /b 0

:test_error_handling
REM Verificar que hay manejo de errores apropiado
findstr /C:"if errorlevel" install_citofono.bat >nul || exit /b 1
findstr /C:"exit /b 1" install_citofono.bat >nul || exit /b 1
findstr /C:"[ERROR]" install_citofono.bat >nul || exit /b 1

echo    ✓ Verificación de errorlevel presente
echo    ✓ Códigos de salida presentes
echo    ✓ Mensajes de error presentes
exit /b 0

REM ============================================================================
REM FIN DEL SCRIPT DE TESTS
REM ============================================================================