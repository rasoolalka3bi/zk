package com.faceattend.app

import android.content.Context
import java.security.SecureRandom

object ApiKeyManager {
    private const val PREFS = "face_attend_prefs"
    private const val KEY_API_KEY = "api_key"

    /** يرجّع المفتاح الحالي، أو يولّد واحدًا جديدًا لأول مرة لو مش موجود. */
    fun getKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_API_KEY, null)
        if (existing != null) return existing

        val newKey = generateRandomKey()
        prefs.edit().putString(KEY_API_KEY, newKey).apply()
        return newKey
    }

    /** يولّد مفتاحًا جديدًا ويستبدل القديم - أي جهة كانت تستخدم المفتاح
     * القديم لن تقدر على السحب بعد الآن حتى تحصل على المفتاح الجديد. */
    fun regenerateKey(context: Context): String {
        val newKey = generateRandomKey()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, newKey).apply()
        return newKey
    }

    private fun generateRandomKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..24).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
