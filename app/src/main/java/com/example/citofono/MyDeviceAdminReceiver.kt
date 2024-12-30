package com.example.citofono

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Device Admin: enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Device Admin: disabled", Toast.LENGTH_SHORT).show()
    }

    /**
     * Se llama cuando tu app entra en Lock Task Mode (Kiosk Mode).
     * Aquí podrías hacer notificaciones extra o configurar lo que necesites.
     */
    override fun onLockTaskModeEntering(
        context: Context,
        intent: Intent,
        pkg: String
    ) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Toast.makeText(context, "Entrando a Kiosk Mode (Lock Task)", Toast.LENGTH_SHORT).show()
    }

    /**
     * Se llama cuando tu app sale del Lock Task Mode (Kiosk Mode).
     * Útil para limpiar cosas o notificar al usuario.
     */
    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Toast.makeText(context, "Saliendo de Kiosk Mode (Lock Task)", Toast.LENGTH_SHORT).show()
    }
}
