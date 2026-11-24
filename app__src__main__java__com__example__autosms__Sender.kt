package com.example.autosms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Sender {
    fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun usedToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("day", "") == today()) p.getInt("used", 0) else 0
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * Roz ki limit (default 100 SMS-parts) tak pending numbers ko SMS bhejta hai.
     * 1 part ka msg -> 100 number, 2 part -> 50, 3 part -> 33 (apne aap, parts ginkar).
     */
    fun run(ctx: Context): Int {
        val p = prefs(ctx)
        val limit = p.getInt("limit", 100)
        val defMsg = p.getString("msg", "") ?: ""
        val db = Db(ctx)
        val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        var used = usedToday(ctx)
        var sentCount = 0
        for ((num, custom) in db.pending()) {
            val text = custom.ifBlank { defMsg }
            if (text.isBlank()) continue
            val parts = sms.divideMessage(text)
            if (used + parts.size > limit) continue
            try {
                sms.sendMultipartTextMessage(num, null, parts, null, null)
                db.markSent(num)            // ek number par dobara SMS nahi jayega
                used += parts.size; sentCount++
                p.edit().putString("day", today()).putInt("used", used).apply()
            } catch (e: Exception) { /* is number ko pending rehne do */ }
            Thread.sleep(3000)               // do SMS ke beech delay
            if (used >= limit) break
        }
        return sentCount
    }
}
