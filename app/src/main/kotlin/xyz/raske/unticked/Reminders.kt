package xyz.raske.unticked

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.annotation.SuppressLint
import android.os.Build
import android.util.AtomicFile
import dev.terminaltodo.core.TaskParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Derived device-local state only. Never writes the selected todo document. */
object Reminders {
    const val CHANNEL = "task-reminders"
    private const val JOB = 7301
    private val executor = Executors.newSingleThreadExecutor()
    fun async(action: () -> Unit) = executor.execute { runCatching(action) }
    fun enabled(context: Context) = context.getSharedPreferences("settings", 0).getBoolean("reminders", false)
    fun allowed(context: Context): Boolean {
        channel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Explicit remind: timestamps from your selected todo.txt" })
    }
    private fun ledger(context: Context) = AtomicFile(File(context.noBackupFilesDir, "reminders.json"))
    private fun load(context: Context): JSONObject {
        val file = ledger(context)
        return if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) JSONObject().put("nextId", 100).put("entries", JSONArray())
        else file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
    }
    private fun save(context: Context, json: JSONObject) {
        val file = ledger(context); val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); stream.fd.sync(); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun records(json: JSONObject) = json.getJSONArray("entries").let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
    private fun pending(context: Context, key: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("dev.terminaltodo.REMIND").setData("unticked://reminder/$key".toUri()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun key(source: String, text: String) = MessageDigest.getInstance("SHA-256").digest((source + "\u0000" + text).toByteArray()).joinToString("") { "%02x".format(it) }
    @SuppressLint("UseKtx") // Check synchronous preference persistence before scheduling work.
    fun configure(context: Context, value: Boolean) {
        check(context.getSharedPreferences("settings", 0).edit().putBoolean("reminders", value).commit())
        val jobs = context.getSystemService(android.app.job.JobScheduler::class.java)
        if (value) check(jobs.schedule(android.app.job.JobInfo.Builder(JOB, ComponentName(context, ReminderJob::class.java))
            .setPeriodic(15 * 60 * 1000L).setPersisted(true).build()) == android.app.job.JobScheduler.RESULT_SUCCESS) { "Android refused background reminder checks" }
        else jobs.cancel(JOB)
        async { reconcile(context) }
    }
    @Synchronized fun reconcile(context: Context): String {
        val json = load(context); val old = records(json)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val source = context.getSharedPreferences("settings", 0).getString("selectedUri", null)
        if (!enabled(context) || source == null) {
            old.forEach { alarm.cancel(pending(context, it.getString("key"))) }
            return if (source == null && enabled(context)) "Select a task file to schedule reminders" else "Reminders off"
        }
        if (!allowed(context)) {
            old.forEach { alarm.cancel(pending(context, it.getString("key"))) }
            return "Notifications blocked · allow them in Android settings"
        }
        val document = try { DocumentStore(context).open(source!!.toUri()) } catch (_: Exception) { return "File unavailable · reminders cannot be verified" }
        if (!document.snapshot.validUtf8) {
            old.forEach { alarm.cancel(pending(context, it.getString("key"))) }
            return "Invalid UTF-8 · reminders paused"
        }
        val tasks = document.snapshot.lines.mapNotNull(TaskParser::parse)
        val counts = tasks.groupingBy { it.line.text }.eachCount()
        val eligible = tasks.filter { !it.completed && it.reminder != null && counts[it.line.text] == 1 }
        val entries = eligible.map { task ->
            val identity = key(source, task.line.text!!)
            old.firstOrNull { it.getString("key") == identity } ?: JSONObject().put("key", identity).put("source", source)
                .put("raw", task.line.text).put("at", task.reminder!!.toEpochMilli()).put("id", json.getInt("nextId").also { json.put("nextId", it + 1) }).put("attempted", false)
        }
        // Keep delivery history for 30 days to avoid repeated notifications after transient edits.
        val history = old.filter { it.optBoolean("attempted") && it.getLong("at") > System.currentTimeMillis() - 30L * 86400000 && entries.none { entry -> entry.getString("key") == it.getString("key") } }
        val activeKeys = entries.map { it.getString("key") }.toSet()
        old.filter { it.getString("key") !in activeKeys }.forEach { alarm.cancel(pending(context, it.getString("key"))); context.getSystemService(NotificationManager::class.java).cancel(it.getInt("id")) }
        json.put("entries", JSONArray(entries + history))
        save(context, json) // durable identity before scheduling
        val now = System.currentTimeMillis()
        val missed = entries.filter { !it.optBoolean("attempted") && it.getLong("at") <= now && it.getLong("at") >= now - 86400000 }
        if (missed.isNotEmpty()) {
            missed.forEach { it.put("attempted", true) }; save(context, json)
            notify(context, 2, "Missed reminders", "${missed.size} reminder(s) found after their scheduled time. Open unticked to review.")
        }
        entries.forEach {
            val operation = pending(context, it.getString("key"))
            if (!it.optBoolean("attempted") && it.getLong("at") > now)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.getLong("at"), operation)
            else alarm.cancel(operation)
        }
        val invalid = tasks.count { !it.completed && it.metadata.any { token -> token.text.startsWith("remind:") } && (it.reminder == null || counts[it.line.text] != 1) }
        val older = entries.count { !it.optBoolean("attempted") && it.getLong("at") < now - 86400000 }
        return "Approximate delivery · ${entries.count { !it.optBoolean("attempted") && it.getLong("at") > now }} scheduled" +
            (if (invalid > 0) " · $invalid invalid/ambiguous" else "") + (if (older > 0) " · $older older missed" else "")
    }
    @Synchronized fun deliver(context: Context, identity: String) {
        if (!enabled(context) || !allowed(context)) return
        val json = load(context)
        val record = records(json).singleOrNull { it.getString("key") == identity } ?: return
        if (record.optBoolean("attempted") || record.getLong("at") > System.currentTimeMillis()) return
        val source = context.getSharedPreferences("settings", 0).getString("selectedUri", null)
        if (source != record.getString("source")) return
        val tasks = runCatching { DocumentStore(context).open(source!!.toUri()).snapshot.let { snapshot ->
            check(snapshot.validUtf8); snapshot.lines.mapNotNull(TaskParser::parse)
        } }.getOrNull()
        val task = tasks?.filter { it.line.text == record.getString("raw") }?.singleOrNull()
        record.put("attempted", true); save(context, json)
        if (tasks == null) notify(context, record.getInt("id"), "Could not verify reminder", "Your task file is unavailable. Open unticked to check it.")
        else if (task != null && !task.completed && task.reminder?.toEpochMilli() == record.getLong("at"))
            notify(context, record.getInt("id"), "unticked", task.description)
    }
    private fun notify(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify(id,
            Notification.Builder(context, CHANNEL).setSmallIcon(xyz.raske.unticked.R.drawable.ic_terminal)
                .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Reminders.async {
            try {
                if (intent.action == "dev.terminaltodo.REMIND") intent.data?.lastPathSegment?.let { Reminders.deliver(context, it) }
                else { Reminders.configure(context, Reminders.enabled(context)); Reminders.reconcile(context) }
            } finally { result.finish() }
        }
    }
}
class ReminderJob : android.app.job.JobService() {
    override fun onStartJob(params: android.app.job.JobParameters): Boolean {
        Reminders.async { try { Reminders.reconcile(this) } finally { jobFinished(params, false) } }
        return true
    }
    override fun onStopJob(params: android.app.job.JobParameters) = true
}
