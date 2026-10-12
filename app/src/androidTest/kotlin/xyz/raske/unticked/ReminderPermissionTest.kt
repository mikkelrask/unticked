package xyz.raske.unticked
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class ReminderPermissionTest {
    @Test fun denialIsVisibleAndDoesNotScheduleNotifications() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(!Reminders.allowed(context)) { "Revoke notifications before this isolated check" }
        try {
            Reminders.configure(context, true)
            assertEquals("Notifications blocked · allow them in Android settings", Reminders.reconcile(context))
        } finally { Reminders.configure(context, false); Reminders.reconcile(context) }
    }
}
