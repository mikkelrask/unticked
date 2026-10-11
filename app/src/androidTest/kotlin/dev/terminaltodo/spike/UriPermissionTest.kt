package dev.terminaltodo.spike

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.terminaltodo.core.TaskParser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only smoke test and an explicit revocation check for the disposable URI only. */
@RunWith(AndroidJUnit4::class)
class UriPermissionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun selectedTaskFileCanBeInspectedWithoutChangingBytes() {
        val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedUri", null)
        assumeTrue("Select a task file first", raw != null)
        val uri = Uri.parse(raw!!)
        val store = DocumentStore(context)
        val before = store.read(uri)
        val document = store.open(uri)
        document.snapshot.lines.forEach { TaskParser.parse(it) }
        assertArrayEquals(before, document.snapshot.bytes())
        assertArrayEquals(before, store.read(uri))
    }
    @Test fun revokedDiagnosticGrantPreventsReading() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run explicitly after the disposable probe tests", args.getString("revokeDiagnostic") == "true")
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val uri = Uri.parse(prefs.getString("diagnosticUri", null) ?: error("Create a diagnostic document first"))
        assertNotEquals(prefs.getString("selectedUri", null), uri.toString())
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == uri })
        try { DocumentStore(context).read(uri); fail("A revoked document must not be readable") }
        catch (_: SecurityException) { /* expected; original task grant is untouched */ }
    }
}
