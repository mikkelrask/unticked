package xyz.raske.unticked

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked separately with an expected synthetic string after host-side changes. Reads only. */
@RunWith(AndroidJUnit4::class)
class ExternalChangeTest {
    @Test fun persistedDocumentResolvesAfterExternalChange() {
        val args = InstrumentationRegistry.getArguments()
        val expected = args.getString("expected")
        val missing = args.getString("expectMissing") == "true"
        assumeTrue("Run separately after an external synthetic change", expected != null || missing)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val uri = Uri.parse(prefs.getString("diagnosticUri", null) ?: error("No diagnostic file selected"))
        if (missing) {
            try { DocumentStore(context).read(uri); fail("A renamed/deleted document must report unavailable") }
            catch (_: java.io.FileNotFoundException) { /* provider correctly rejects the old path */ }
        } else assertEquals(expected + "\n", DocumentStore(context).read(uri).toString(Charsets.UTF_8))
    }
}
