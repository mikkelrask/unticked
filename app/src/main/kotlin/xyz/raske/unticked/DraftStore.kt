package xyz.raske.unticked

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import dev.terminaltodo.core.DocumentSnapshot
import org.json.JSONObject
import java.io.File

data class TaskDraft(val source: String, val base: DocumentSnapshot, val line: Int?, val text: String, val kind: String = "edit", val proposedHash: String? = null)

/** One complete atomic draft, including its original file snapshot and source URI. */
class DraftStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "task-draft.json"))
    fun load(): TaskDraft? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val json = file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        check(json.getInt("schema") == 1) { "Unsupported draft format" }
        return TaskDraft(json.getString("source"), DocumentSnapshot.from(Base64.decode(json.getString("base"), Base64.NO_WRAP)),
            if (json.isNull("line")) null else json.getInt("line"), json.getString("text"), json.getString("kind"), if (json.isNull("proposedHash")) null else json.getString("proposedHash"))
    }
    fun save(draft: TaskDraft) {
        val bytes = JSONObject().put("schema", 1).put("source", draft.source).put("base", Base64.encodeToString(draft.base.bytes(), Base64.NO_WRAP))
            .put("line", draft.line ?: JSONObject.NULL).put("text", draft.text).put("kind", draft.kind).put("proposedHash", draft.proposedHash ?: JSONObject.NULL).toString().toByteArray()
        val out = file.startWrite()
        try { out.write(bytes); out.fd.sync(); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun clear() { file.delete() }
}
