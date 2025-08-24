package com.example.aihealth.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth

object AuthUtils {
    private const val PREF = "auth_prefs"
    private const val KEY_LAST_EMAIL = "auth.last_email"

    /** 자바에서 AuthUtils.currentEmail(ctx, intent, true) 형태로 바로 호출 가능하도록 @JvmStatic 추가 */
    @JvmStatic
    fun currentEmail(
        context: Context,
        fallbackIntent: Intent? = null,
        cacheIfFound: Boolean = true
    ): String? {
        val viaIntent = fallbackIntent?.getStringExtra("user_email")
        if (!viaIntent.isNullOrBlank()) {
            if (cacheIfFound) cacheLastEmail(context, viaIntent)
            return viaIntent
        }

        val viaFirebase = FirebaseAuth.getInstance().currentUser?.email
        if (!viaFirebase.isNullOrBlank()) {
            if (cacheIfFound) cacheLastEmail(context, viaFirebase)
            return viaFirebase
        }

        val viaGoogle = GoogleSignIn.getLastSignedInAccount(context)?.email
        if (!viaGoogle.isNullOrBlank()) {
            if (cacheIfFound) cacheLastEmail(context, viaGoogle)
            return viaGoogle
        }

        return readLastEmail(context)
    }

    /** 자바에서 AuthUtils.cacheLastEmail(ctx, email)로 호출 가능 */
    @JvmStatic
    fun cacheLastEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_LAST_EMAIL, email).apply()
    }

    /** 자바에서 AuthUtils.readLastEmail(ctx)로 호출 가능 */
    @JvmStatic
    fun readLastEmail(context: Context): String? =
        prefs(context).getString(KEY_LAST_EMAIL, null)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
