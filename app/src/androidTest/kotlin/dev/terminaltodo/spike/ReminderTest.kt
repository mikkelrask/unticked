package dev.terminaltodo.spike

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ReminderTest {
    @Test fun syntheticReminderReconcilesDeliversOnceAndSuppressesStaleTasks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(InstrumentationRegistry.getArguments().getString("enableM2Writes") == "true")
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val source = prefs.getString("selectedUri", null) ?: error("Select synthetic file")
        val uri = Uri.parse(source)
        val store = DocumentStore(context)
        check(store.open(uri).name == "terminal-todo-m2-test.txt")
        check(Reminders.allowed(context)) { "Grant notifications for emulator test" }
        val original = store.read(uri)
        val wasEnabled = Reminders.enabled(context)
        val ledger = File(context.noBackupFilesDir, "reminders.json")
        val oldLedger = if (ledger.exists()) ledger.readBytes() else null
        val manager = context.getSystemService(NotificationManager::class.java)
        fun entries() = JSONObject(ledger.readText()).getJSONArray("entries")
        fun line(seconds: Long) = "Synthetic reminder +test remind:" + OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(seconds).withNano(0)
        try {
            Reminders.configure(context, true)
            val raw = "Synthetic reminder +test remind:" + OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(3).withNano(123456789)
            store.port(uri).writeTruncated((raw + "\n").toByteArray())
            assertTrue(Reminders.reconcile(context).contains("1 scheduled"))
            val entry = entries().getJSONObject(0)
            val id = entry.getInt("id")
            val identity = entry.getString("key")
            Thread.sleep(3500)
            context.sendBroadcast(Intent(context, ReminderReceiver::class.java).setAction("dev.terminaltodo.REMIND").setData(Uri.parse("unticked://reminder/$identity")))
            val deadline = System.currentTimeMillis() + 8000
            while (manager.activeNotifications.none { it.id == id } && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertTrue(manager.activeNotifications.any { it.id == id })
            assertTrue(entries().getJSONObject(0).getBoolean("attempted"))
            manager.cancel(id)
            Reminders.deliver(context, identity)
            assertTrue(manager.activeNotifications.none { it.id == id })

            val stale = line(1)
            store.port(uri).writeTruncated((stale + "\n").toByteArray())
            Reminders.reconcile(context)
            val obsolete = entries().getJSONObject(0)
            store.port(uri).writeTruncated(("x 2026-10-11 " + stale + "\n").toByteArray())
            Thread.sleep(1500)
            Reminders.deliver(context, obsolete.getString("key"))
            assertTrue(manager.activeNotifications.none { it.id == obsolete.getInt("id") })
            Reminders.reconcile(context)
            assertTrue(Reminders.reconcile(context).contains("0 scheduled"))

            val duplicate = line(60)
            store.port(uri).writeTruncated(("$duplicate\n$duplicate\ninvalid remind:broken\n").toByteArray())
            val status = Reminders.reconcile(context)
            assertTrue(status.contains("0 scheduled"))
            assertTrue(status.contains("3 invalid/ambiguous"))

            store.port(uri).writeTruncated((line(-30) + "\n").toByteArray())
            Reminders.reconcile(context)
            assertTrue(manager.activeNotifications.any { it.id == 2 })
            manager.cancel(2)
            Reminders.reconcile(context)
            assertTrue(manager.activeNotifications.none { it.id == 2 })

            Reminders.configure(context, false)
            assertEquals("Reminders off", Reminders.reconcile(context))
        } finally {
            Reminders.configure(context, false)
            Reminders.reconcile(context)
            store.port(uri).writeTruncated(original)
            if (oldLedger != null) ledger.writeBytes(oldLedger) else ledger.delete()
            Reminders.configure(context, wasEnabled)
            manager.cancelAll()
        }
    }
}
