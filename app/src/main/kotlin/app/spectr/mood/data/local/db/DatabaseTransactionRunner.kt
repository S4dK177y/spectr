package app.spectr.mood.data.local.db

/**
 * Abstraction over Room transactions so repositories can be unit-tested cleanly on the JVM.
 */
interface DatabaseTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

/**
 * Abstraction over wall-clock UTC epoch milliseconds for deterministic testing.
 */
fun interface EpochClock {
    fun nowMillis(): Long
}
