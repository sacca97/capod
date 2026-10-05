package eu.darken.capod.debug.wakescan

/**
 * Debug-only wake scan: whether an authenticated lid-open advert should trigger a connect.
 *
 * Any authenticated lid-open advert counts as satisfying WHEN_SEEN and CASE_OPEN; other auto connect conditions
 * (e.g. IN_EAR) are not evaluated here.
 */
object WakeScanDecision {

    const val THROTTLE_MS = 30_000L

    enum class Reason { AUTO_CONNECT_OFF, NO_ADDRESS, ALREADY_CONNECTED, THROTTLED }

    sealed interface Result {
        data object Connect : Result
        data class Skip(val reason: Reason) : Result
    }

    /** [millisSinceLastAttempt] is null if there was no earlier attempt for this profile. */
    fun decide(
        autoConnectEnabled: Boolean,
        hasAddress: Boolean,
        isAlreadyConnected: Boolean,
        millisSinceLastAttempt: Long?,
        throttleMs: Long = THROTTLE_MS,
    ): Result = when {
        !autoConnectEnabled -> Result.Skip(Reason.AUTO_CONNECT_OFF)
        !hasAddress -> Result.Skip(Reason.NO_ADDRESS)
        isAlreadyConnected -> Result.Skip(Reason.ALREADY_CONNECTED)
        millisSinceLastAttempt != null && millisSinceLastAttempt < throttleMs -> Result.Skip(Reason.THROTTLED)
        else -> Result.Connect
    }
}
