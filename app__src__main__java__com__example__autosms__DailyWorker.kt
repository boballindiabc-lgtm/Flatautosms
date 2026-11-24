package com.example.autosms

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result { Sender.run(applicationContext); return Result.success() }

    companion object {
        fun schedule(ctx: Context, hour: Int) {
            val now = Calendar.getInstance()
            val t = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(t.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("daily_sms", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("daily_sms")
    }
}
