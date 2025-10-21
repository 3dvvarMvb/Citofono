// SessionManager.kt
package com.example.citofono

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object SessionManager {
    private const val PREF = "auth_session"
    private const val K_SESSION = "session_id"
    private const val K_ROLE = "role"
    private const val K_USER = "username"

    fun save(ctx: Context, sessionId: String, username: String, role: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_SESSION, sessionId)
            .putString(K_ROLE, role)
            .putString(K_USER, username)
            .apply()
    }
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun sessionId(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K_SESSION, "") ?: ""
    fun role(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K_ROLE, "user") ?: "user"
    fun username(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K_USER, "") ?: ""
    fun isLoggedIn(ctx: Context) = sessionId(ctx).isNotBlank()

    /** Cierra sesión local + pide logout al backend y va a AuthActivity sin autologin. */
    fun logoutAndGoToLogin(activity: Activity) {
        val sid = sessionId(activity)

        // Solo si la Activity implementa LifecycleOwner (ComponentActivity lo hace)
        (activity as? LifecycleOwner)?.lifecycleScope?.launch {
            runCatching { if (sid.isNotBlank()) EsbApi.authLogout(sid) }
        }

        clear(activity)

        val i = Intent(activity, AuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("skip_auto_login", true)
        }
        activity.startActivity(i)
        activity.finishAffinity()
    }
}
