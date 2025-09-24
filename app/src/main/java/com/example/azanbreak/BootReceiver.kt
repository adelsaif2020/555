package com.example.azanbreak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // re-schedule all stored breaks and today's prayers
        val prefs = context.getSharedPreferences("azan_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat", 24.5077f).toDouble()
        val lon = prefs.getFloat("lon", 44.3924f).toDouble()
        // schedule prayers
        scheduleAllPrayers(context, lat, lon)
        // schedule breaks
        val s = prefs.getString("breaks_json","[]") ?: "[]"
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val enabled = o.optBoolean("enabled", true)
                if (!enabled) continue
                val id = o.optString("id")
                val time = o.optString("time")
                val duration = o.optInt("duration", 10)
                val b = com.example.azanbreak.BreakItem(id, o.optString("name","بريك"), time, duration, enabled, o.optString("startUri",""), o.optString("endUri",""))
                scheduleBreak(context, b)
            }
        } catch (e: Exception) { }
    }
}
