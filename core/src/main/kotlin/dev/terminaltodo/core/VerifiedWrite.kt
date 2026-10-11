package dev.terminaltodo.core

interface DocumentPort {
    fun read(): ByteArray
    fun writeTruncated(bytes: ByteArray)
}
interface RecoveryJournal {
    /** Must finish durable recovery recording before returning. */
    fun prepare(before: ByteArray, proposed: ByteArray): String
    fun verified(recordId: String)
}
sealed interface WriteResult {
    data class Verified(val recordId: String) : WriteResult
    data object Conflict : WriteResult
    data class Failed(val recordId: String?, val explanation: String) : WriteResult
}

/** M0 primitive, used only on a newly created diagnostic document. No atomicity claim. */
class VerifiedWriter(private val port: DocumentPort, private val journal: RecoveryJournal) {
    @Synchronized
    fun write(base: ByteArray, proposed: ByteArray): WriteResult {
        var record: String? = null
        return try {
            val current = port.read()
            if (!current.contentEquals(base)) return WriteResult.Conflict
            record = journal.prepare(current, proposed)
            // An independent writer can still change the document between this read and write.
            port.writeTruncated(proposed)
            check(port.read().contentEquals(proposed)) { "Read-back did not match; recovery retained" }
            journal.verified(record)
            WriteResult.Verified(record)
        } catch (e: Exception) {
            WriteResult.Failed(record, e.message ?: e.javaClass.simpleName)
        }
    }
}
