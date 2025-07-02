# ============================================================================
# Citofono PowerShell Installer
# Instalador avanzado con interfaz gráfica opcional
# ============================================================================

param(
    [switch]$GUI,
    [switch]$Verbose,
    [string]$ConfigFile = "config.ini",
    [string]$APKPath = "app-debug.apk"
)

# Configuración
$Script:Config = @{
    APP_PACKAGE = "com.example.citofono"
    DEVICE_ADMIN_RECEIVER = "com.example.citofono/.MyDeviceAdminReceiver"
    APK_NAME = $APKPath
}

# Colores para consola
$Script:Colors = @{
    Red = "Red"
    Green = "Green"
    Yellow = "Yellow"
    Blue = "Cyan"
    White = "White"
}

function Write-ColorText {
    param([string]$Text, [string]$Color = "White")
    Write-Host $Text -ForegroundColor $Script:Colors[$Color]
}

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-ColorText "============================================================================" "Blue"
    Write-ColorText "                    $Title" "Blue"
    Write-ColorText "============================================================================" "Blue"
    Write-Host ""
}

function Load-Configuration {
    if (Test-Path $ConfigFile) {
        Write-ColorText "[INFO] Cargando configuración desde $ConfigFile..." "Yellow"
        $content = Get-Content $ConfigFile
        foreach ($line in $content) {
            if ($line -match "^([^#=]+)=(.+)$") {
                $key = $matches[1].Trim()
                $value = $matches[2].Trim()
                if ($Script:Config.ContainsKey($key)) {
                    $Script:Config[$key] = $value
                }
            }
        }
    }
}

function Test-ADBAvailable {
    Write-ColorText "[PASO 1/6] Verificando disponibilidad de ADB..." "Yellow"
    try {
        $adbVersion = & adb version 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-ColorText "[OK] ADB encontrado y funcionando." "Green"
            return $true
        }
    }
    catch {
        Write-ColorText "[ERROR] ADB no está disponible en el sistema." "Red"
        Write-ColorText "        Por favor instale Android SDK Platform Tools." "Red"
        return $false
    }
    return $false
}

function Test-APKExists {
    Write-ColorText "[PASO 2/6] Verificando archivo APK..." "Yellow"
    if (Test-Path $Script:Config.APK_NAME) {
        Write-ColorText "[OK] APK encontrado: $($Script:Config.APK_NAME)" "Green"
        return $true
    }
    else {
        Write-ColorText "[ERROR] No se encontró el archivo APK: $($Script:Config.APK_NAME)" "Red"
        Write-ColorText "        Asegúrese de que el APK esté en la carpeta actual." "Red"
        return $false
    }
}

function Get-ConnectedDevices {
    Write-ColorText "[PASO 3/6] Detectando dispositivos Android conectados..." "Yellow"
    $devices = & adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] }
    
    if ($devices.Count -eq 0) {
        Write-ColorText "[ERROR] No se detectaron dispositivos Android conectados." "Red"
        Write-ColorText "        Verifique la conexión USB y la depuración USB." "Red"
        return @()
    }
    
    Write-ColorText "[OK] Detectados $($devices.Count) dispositivo(s) conectado(s)." "Green"
    
    if ($devices.Count -gt 1) {
        Write-Host ""
        Write-ColorText "[INFO] Dispositivos detectados:" "Yellow"
        & adb devices
        Write-Host ""
        if (-not $GUI) {
            $continue = Read-Host "¿Desea continuar con todos los dispositivos? (S/N)"
            if ($continue -ne "S" -and $continue -ne "s") {
                Write-ColorText "[INFO] Instalación cancelada por el usuario." "Yellow"
                return @()
            }
        }
    }
    
    return $devices
}

function Test-DeviceSettings {
    param([array]$Devices)
    
    Write-ColorText "[PASO 4/6] Verificando configuración de dispositivos..." "Yellow"
    
    foreach ($device in $Devices) {
        Write-ColorText "[INFO] Verificando dispositivo: $device" "Yellow"
        
        # Verificar depuración USB
        $usbDebugging = & adb -s $device shell settings get global adb_enabled 2>$null
        if ($usbDebugging -ne "1") {
            Write-ColorText "[ERROR] La depuración USB no está habilitada en $device" "Red"
            return $false
        }
        
        # Verificar versión de Android
        $apiLevel = & adb -s $device shell getprop ro.build.version.sdk 2>$null
        if ([int]$apiLevel -lt 21) {
            Write-ColorText "[ERROR] El dispositivo $device tiene Android API $apiLevel (se requiere 21+)" "Red"
            return $false
        }
        
        Write-ColorText "[OK] Dispositivo $device - Android API $apiLevel compatible" "Green"
    }
    
    return $true
}

function Clear-PreviousInstallation {
    param([array]$Devices)
    
    Write-ColorText "[PASO 5/6] Limpiando instalaciones previas..." "Yellow"
    
    foreach ($device in $Devices) {
        Write-ColorText "[INFO] Limpiando dispositivo: $device" "Yellow"
        
        # Verificar si la app está instalada
        $appInstalled = & adb -s $device shell pm list packages | Select-String $Script:Config.APP_PACKAGE
        
        if ($appInstalled) {
            Write-ColorText "[INFO] Aplicación encontrada, removiendo permisos..." "Yellow"
            
            # Remover Device Owner
            & adb -s $device shell dpm remove-active-admin $Script:Config.DEVICE_ADMIN_RECEIVER 2>$null
            
            # Desinstalar aplicación
            Write-ColorText "[INFO] Desinstalando aplicación previa..." "Yellow"
            & adb -s $device uninstall $Script:Config.APP_PACKAGE >$null 2>&1
        }
        
        # Desinstalación de seguridad
        & adb -s $device uninstall $Script:Config.APP_PACKAGE >$null 2>&1
        
        Write-ColorText "[OK] Limpieza completada para dispositivo $device" "Green"
    }
}

function Install-AndSetupDeviceOwner {
    param([array]$Devices)
    
    Write-ColorText "[PASO 6/6] Instalando Citofono con permisos de Device Owner..." "Yellow"
    
    foreach ($device in $Devices) {
        Write-Host ""
        Write-ColorText "[INFO] Procesando dispositivo: $device" "Yellow"
        
        # Instalar APK
        Write-ColorText "[INFO] Instalando APK..." "Yellow"
        $installResult = & adb -s $device install $Script:Config.APK_NAME 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-ColorText "[ERROR] Error al instalar APK en $device" "Red"
            Write-ColorText "        $installResult" "Red"
            continue
        }
        Write-ColorText "[OK] APK instalado correctamente" "Green"
        
        # Pausa para registro del sistema
        Start-Sleep -Seconds 2
        
        # Configurar Device Owner
        Write-ColorText "[INFO] Configurando permisos de Device Owner..." "Yellow"
        $deviceOwnerResult = & adb -s $device shell dpm set-device-owner $Script:Config.DEVICE_ADMIN_RECEIVER 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-ColorText "[ERROR] Error al configurar Device Owner en $device" "Red"
            Write-ColorText "        $deviceOwnerResult" "Red"
            Write-ColorText "[INFO] Solución: Realizar factory reset sin configurar cuentas" "Yellow"
            continue
        }
        Write-ColorText "[OK] Device Owner configurado correctamente" "Green"
        
        # Verificar configuración
        Write-ColorText "[INFO] Verificando configuración..." "Yellow"
        $verification = & adb -s $device shell dpm list-owners | Select-String $Script:Config.APP_PACKAGE
        if (-not $verification) {
            Write-ColorText "[WARNING] No se pudo verificar la configuración de Device Owner" "Red"
        } else {
            Write-ColorText "[OK] Device Owner verificado correctamente" "Green"
        }
        
        # Iniciar aplicación
        Write-ColorText "[INFO] Iniciando aplicación..." "Yellow"
        & adb -s $device shell monkey -p $Script:Config.APP_PACKAGE -c android.intent.category.LAUNCHER 1 >$null 2>&1
        
        Write-ColorText "[COMPLETADO] Dispositivo $device configurado exitosamente" "Green"
    }
}

function Show-CompletionMessage {
    Write-Host ""
    Write-Header "INSTALACIÓN COMPLETADA"
    Write-ColorText "[ÉXITO] Citofono ha sido instalado correctamente con permisos de Device Owner." "Green"
    Write-ColorText "        La aplicación debería iniciar automáticamente en modo Kiosk." "Green"
    Write-Host ""
    Write-ColorText "[INFO] Para desinstalar, ejecute: .\uninstall_citofono.ps1" "Yellow"
    Write-Host ""
}

# ============================================================================
# FUNCIÓN PRINCIPAL
# ============================================================================

function Start-Installation {
    Write-Header "CITOFONO - INSTALADOR AUTOMÁTICO"
    
    # Cargar configuración
    Load-Configuration
    
    # Verificaciones
    if (-not (Test-ADBAvailable)) { return }
    if (-not (Test-APKExists)) { return }
    
    $devices = Get-ConnectedDevices
    if ($devices.Count -eq 0) { return }
    
    if (-not (Test-DeviceSettings -Devices $devices)) { return }
    
    # Instalación
    Clear-PreviousInstallation -Devices $devices
    Install-AndSetupDeviceOwner -Devices $devices
    
    # Mensaje de finalización
    Show-CompletionMessage
}

# Ejecutar instalación
if ($MyInvocation.InvocationName -ne '.') {
    Start-Installation
}