package com.example.azanbreak

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        // run as foreground to increase likelihood of execution
        setForeground(createForegroundInfo("تشغيل تنبيه..."))
        val type = inputData.getString("type") ?: ""
        val prefs = applicationContext.getSharedPreferences("azan_prefs", Context.MODE_PRIVATE)
        try {
            when(type) {
                "test_adhan" -> {
                    val u = prefs.getString("adhanUri","")
                    playUri(u)
                    NotificationUtils.buildNotification(applicationContext, "اختبار الأذان", "تم تشغيل صوت الأذان").also {
                        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis()%10000).toInt(), it.build())
                    }
                }
                "pray" -> {
                    val pray = inputData.getString("pray") ?: "صلاة"
                    val u = prefs.getString("adhanUri","")
                    playUri(u)
                    NotificationUtils.buildNotification(applicationContext, "الأذان", "وقت: $pray").also {
                        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis()%10000).toInt(), it.build())
                    }
                    // handle auto break scheduling is handled by app logic when scheduling prayers
                }
                "break_start" -> {
                    val id = inputData.getString("breakId") ?: ""
                    val b = findBreakById(id)
                    val u = b?.startUri ?: prefs.getString("breakStartUri","")
                    playUri(u)
                    NotificationUtils.buildNotification(applicationContext, "بريك", "بدأ ${b?.name ?: "بريك"}").also {
                        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis()%10000).toInt(), it.build())
                    }
                }
                "break_end" -> {
                    val id = inputData.getString("breakId") ?: ""
                    val b = findBreakById(id)
                    val u = b?.endUri ?: prefs.getString("breakEndUri","")
                    playUri(u)
                    NotificationUtils.buildNotification(applicationContext, "انتهى البريك", "${b?.name ?: "بريك"} انتهى").also {
                        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis()%10000).toInt(), it.build())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Result.success()
    }

    private suspend fun playUri(uriString: String?) = withContext(Dispatchers.IO) {
        try {
            if (!uriString.isNullOrEmpty()) {
                val u = Uri.parse(uriString)
                val mp = MediaPlayer.create(applicationContext, u)
                mp?.start()
                // play briefly (workers should finish quickly; we let media play in background)
            } else {
                // fallback tone
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = (System.currentTimeMillis()%10000).toInt()
        val notification = NotificationUtils.buildNotification(applicationContext, "منبه", progress).build()
        return ForegroundInfo(id, notification)
    }

    private fun findBreakById(id: String): com.example.azanbreak.BreakItem? {
        val prefs = applicationContext.getSharedPreferences("azan_prefs", Context.MODE_PRIVATE)
        val s = prefs.getString("breaks_json","[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    return com.example.azanbreak.BreakItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        time = o.optString("time"),
                        duration = o.optInt("duration"),
                        enabled = o.optBoolean("enabled", true),
                        startUri = o.optString("startUri", null).takeIf { it!=null && it.isNotEmpty() },
                        endUri = o.optString("endUri", null).takeIf { it!=null && it.isNotEmpty() }
                    )
                }
            }
        } catch (e: Exception) { }
        return null
    }
}
