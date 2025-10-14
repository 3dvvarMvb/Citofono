package com.example.citofono

object NetConfig {
    const val BUS_HOST = "127.0.0.1"  // via adb reverse
    const val BUS_PORT = 5000
    private fun isEmulator(): Boolean {
        val f = android.os.Build.FINGERPRINT
        val m = android.os.Build.MODEL
        val b = android.os.Build.BRAND
        val d = android.os.Build.DEVICE
        val p = android.os.Build.PRODUCT
        return f.startsWith("generic") || f.startsWith("unknown") ||
               m.contains("Emulator", true) || m.contains("Android SDK built for x86", true) ||
               b.startsWith("generic") && d.startsWith("generic") ||
               p == "google_sdk"
    }
}
