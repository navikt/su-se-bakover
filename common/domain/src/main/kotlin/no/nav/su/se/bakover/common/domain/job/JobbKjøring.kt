package no.nav.su.se.bakover.common.domain.job

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Representerer én kjøring av en schedulert jobb.
 */
data class JobbKjøring(
    val id: UUID,
    val jobbNavn: String,
    val status: JobbKjøringStatus,
    val startetTidspunkt: Instant,
    val ferdigTidspunkt: Instant?,
    val feilmelding: String?,
    val intervall: Duration,
) {
    companion object {
        fun startet(jobbNavn: String, intervall: Duration): JobbKjøring = JobbKjøring(
            id = UUID.randomUUID(),
            jobbNavn = jobbNavn,
            status = JobbKjøringStatus.KJØRER,
            startetTidspunkt = Instant.now(),
            ferdigTidspunkt = null,
            feilmelding = null,
            intervall = intervall,
        )
    }

    fun fullført(): JobbKjøring = copy(
        status = JobbKjøringStatus.FULLFØRT,
        ferdigTidspunkt = Instant.now(),
    )

    fun feilet(melding: String?): JobbKjøring = copy(
        status = JobbKjøringStatus.FEILET,
        ferdigTidspunkt = Instant.now(),
        feilmelding = melding,
    )
}

enum class JobbKjøringStatus {
    KJØRER,
    FULLFØRT,
    FEILET,
}
