package com.example.azanbreak

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class BreakItem(
    val id: String,
    var name: String,
    var time: String, // "HH:mm"
    var duration: Int,
    var enabled: Boolean,
    var startUri: String?,
    var endUri: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationUtils.createChannelIfNeeded(this)
        setContent {
            MaterialTheme {
                AzanBreakApp()
            }
        }
    }
}

@Composable
fun AzanBreakApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("azan_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    // UI state mirrors saved prefs
    var cityLat by remember { mutableStateOf(prefs.getFloat("lat", 24.5077f).toDouble()) }
    var cityLon by remember { mutableStateOf(prefs.getFloat("lon", 44.3924f).toDouble()) }
    val timeZone = "Asia/Riyadh"

    // loads breaks from prefs
    fun loadBreaks(): MutableList<BreakItem> {
        val arr = mutableListOf<BreakItem>()
        val s = prefs.getString("breaks_json", "[]") ?: "[]"
        try {
            val j = JSONArray(s)
            for (i in 0 until j.length()) {
                val o = j.getJSONObject(i)
                arr.add(BreakItem(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name","بريك"),
                    time = o.optString("time","00:00"),
                    duration = o.optInt("duration",10),
                    enabled = o.optBoolean("enabled",true),
                    startUri = o.optString("startUri", null).takeIf { it!=null && it.isNotEmpty() },
                    endUri = o.optString("endUri", null).takeIf { it!=null && it.isNotEmpty() }
                ))
            }
        } catch (e: Exception) { }
        return arr
    }

    var breaks by remember { mutableStateOf(loadBreaks()) }

    fun saveBreaks() {
        val j = JSONArray()
        breaks.forEach { b ->
            val o = JSONObject()
            o.put("id", b.id)
            o.put("name", b.name)
            o.put("time", b.time)
            o.put("duration", b.duration)
            o.put("enabled", b.enabled)
            o.put("startUri", b.startUri ?: "")
            o.put("endUri", b.endUri ?: "")
            j.put(o)
        }
        prefs.edit().putString("breaks_json", j.toString()).apply()
    }

    // file pickers for start/end sounds with persistable permission
    val pickStartSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // assign to last edited break in UI flow (we set a temp var)
        }
    }
    // We'll use a generic OpenDocument launcher and assign URIs from dialogs below.

    // UI
    Surface(modifier = Modifier.fillMaxSize().background(Color(0xFF071228))) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_alarm),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("منبه أذان وبريك — مصنع الحرمين", color = Color(0xFFE6EEF6), fontSize = 20.sp)
                    Text("يعمل التطبيق بدون استخدام الإنترنت", color = Color(0xFF9AA4B2), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Location card
            Card(modifier = Modifier.fillMaxWidth(), backgroundColor = Color(0x0DFFFFFF), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("الموقع (الدوادمي) — إحداثيات", color = Color(0xFF9AA4B2))
                    Text("lat: ${cityLat}, lon: ${cityLon} — timezone: $timeZone", color = Color(0xFFE6EEF6), fontSize = 12.sp)
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = {
                            // TODO: request location - for skeleton show toast
                        }) { Text("اكتشاف تلقائي للموقع") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            cityLat = 24.5077; cityLon = 44.3924
                            prefs.edit().putFloat("lat", cityLat.toFloat()).putFloat("lon", cityLon.toFloat()).apply()
                            // re-schedule if needed
                        }) { Text("اعادة الدوادمي افتراضياً") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Prayer times card (compact)
            Card(modifier = Modifier.fillMaxWidth(), backgroundColor = Color(0x0DFFFFFF), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("أوقات الصلاة لليوم (حساب: أمّ القرى — مذهب: شافعي)", color = Color(0xFFE6EEF6))
                    val times = computePrayerTimes(cityLat, cityLon)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            times.forEach { (k, v) ->
                                Text(k, color = Color(0xFF9AA4B2))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            times.forEach { (_, v) ->
                                Text(v, color = Color(0xFFE6EEF6))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            // schedule prayers using WorkManager (implemented below)
                            scheduleAllPrayers(context = LocalContext.current, lat = cityLat, lon = cityLon)
                        }) { Text("تمكين الإشعارات") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            // refresh
                        }) { Text("تحديث الأوقات") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Adhan selection card simplified (user can pick audio)
            Card(modifier = Modifier.fillMaxWidth(), backgroundColor = Color(0x0DFFFFFF), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("صوت الأذان", color = Color(0xFFE6EEF6))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var adhanUri by remember { mutableStateOf(prefs.getString("adhanUri","") ?: "") }
                        val pickAdhan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                            uri?.let {
                                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                adhanUri = it.toString()
                                prefs.edit().putString("adhanUri", adhanUri).apply()
                            }
                        }
                        Button(onClick = { pickAdhan.launch(arrayOf("audio/*")) }) { Text("اختر صوت الأذان") }
                        Spacer(Modifier.width(8.dp))
                        Text(if (adhanUri.isNotEmpty()) "ملف محدد" else "لا يوجد ملف محدد", color = Color(0xFF9AA4B2))
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            // test adhan by enqueuing immediate worker
                            CoroutineScope(Dispatchers.IO).launch {
                                scheduleTestPlay(context = LocalContext.current)
                            }
                        }) { Text("اختبار الأذان") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Breaks management - list + add
            Card(modifier = Modifier.fillMaxWidth().weight(1f), backgroundColor = Color(0x0DFFFFFF), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("مواعيد البريك — يمكنك إضافة أكثر من موعد", color = Color(0xFFE6EEF6))
                    Spacer(Modifier.height(8.dp))
                    // add break area
                    var newName by remember { mutableStateOf("") }
                    var newTime by remember { mutableStateOf("") }
                    var newDuration by remember { mutableStateOf("10") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("اسم البريك") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = newTime, onValueChange = { newTime = it }, label = { Text("HH:mm") }, modifier = Modifier.width(110.dp))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = newDuration, onValueChange = { newDuration = it }, label = { Text("مدة (د)") }, modifier = Modifier.width(90.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            // validate and add
                            val t = if (newTime.matches(Regex("^\\d{1,2}:\\d{2}\$"))) newTime else return@Button
                            val dur = newDuration.toIntOrNull() ?: 10
                            val b = BreakItem(UUID.randomUUID().toString(), if (newName.isBlank()) "بريك" else newName, t, dur, true, null, null)
                            breaks = (breaks + b).toMutableList()
                            saveBreaks()
                            // schedule this break using workmanager
                            scheduleBreak(context, b)
                            newName = ""; newTime = ""; newDuration = "10"
                        }) { Text("إضافة") }
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))

                    // breaks list
                    LazyColumn {
                        itemsIndexed(breaks) { idx, b ->
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(Color(0x05000000), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.name, color = Color(0xFFE6EEF6))
                                    Text("${b.time} — مدة: ${b.duration} دقيقة", color = Color(0xFF9AA4B2), fontSize = 12.sp)
                                }
                                Switch(checked = b.enabled, onCheckedChange = {
                                    b.enabled = it; breaks = breaks.toMutableList(); saveBreaks()
                                })
                                IconButton(onClick = {
                                    // edit dialog simple flow: open a dialog (handled below)
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "edit", tint = Color(0xFFE6EEF6))
                                }
                                IconButton(onClick = {
                                    // delete
                                    breaks = breaks.toMutableList().also { it.removeAt(idx) }
                                    saveBreaks()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color(0xFFE6EEF6))
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

fun computePrayerTimes(lat: Double, lon: Double): Map<String, String> {
    return try {
        val coords = Coordinates(lat, lon)
        val params = CalculationMethod.UmmAlQura()
        params.madhab = Madhab.Shafi
        val now = Date()
        val pt = PrayerTimes(coords, now, params)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        mapOf(
            "الفجر" to fmt.format(pt.fajr.toDate()),
            "الشروق" to fmt.format(pt.sunrise.toDate()),
            "الظهر" to fmt.format(pt.dhuhr.toDate()),
            "العصر" to fmt.format(pt.asr.toDate()),
            "المغرب" to fmt.format(pt.maghrib.toDate()),
            "العشاء" to fmt.format(pt.isha.toDate())
        )
    } catch (e: Exception) {
        mapOf("خطأ" to "خطأ في الحساب")
    }
}

// Scheduling helpers (WorkManager-based) - simplified scheduling that enqueues OneTimeWorkRequest with initialDelay
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

fun scheduleAllPrayers(context: Context, lat: Double, lon: Double) {
    val coords = Coordinates(lat, lon)
    val params = CalculationMethod.UmmAlQura()
    params.madhab = Madhab.Shafi
    val today = Calendar.getInstance()
    val times = PrayerTimes(coords, today.time, params)
    val map = mapOf("fajr" to times.fajr, "dhuhr" to times.dhuhr, "asr" to times.asr, "maghrib" to times.maghrib, "isha" to times.isha)
    map.forEach { (k, v) ->
        val trigger = v.toDate().time
        scheduleWorkerAt(context, trigger, "pray", mapOf("pray" to k))
    }
}

fun scheduleBreak(context: Context, b: BreakItem) {
    // parse HH:mm for today
    try {
        val parts = b.time.split(":")
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        cal.set(Calendar.MINUTE, parts[1].toInt())
        cal.set(Calendar.SECOND, 0)
        val trigger = cal.timeInMillis
        scheduleWorkerAt(context, trigger, "break_start", mapOf("breakId" to b.id))
        // schedule end
        val endTrigger = trigger + b.duration * 60000L
        scheduleWorkerAt(context, endTrigger, "break_end", mapOf("breakId" to b.id))
    } catch (e: Exception) { }
}

fun scheduleTestPlay(context: Context) {
    val now = System.currentTimeMillis()
    scheduleWorkerAt(context, now + 2000L, "test_adhan", emptyMap())
}

fun scheduleWorkerAt(context: Context, triggerAtMillis: Long, type: String, extras: Map<String, String>) {
    val now = System.currentTimeMillis()
    var delay = triggerAtMillis - now
    if (delay < 0) {
        // schedule for tomorrow
        delay += 24 * 3600 * 1000L
    }
    val data = Data.Builder().putString("type", type)
    extras.forEach { (k, v) -> data.putString(k, v) }
    val req = OneTimeWorkRequestBuilder<AlarmWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
        .setInputData(data.build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(type + (extras[extras.keys.firstOrNull()] ?: "") + triggerAtMillis, ExistingWorkPolicy.REPLACE, req)
}
