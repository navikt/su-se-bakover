package no.nav.su.se.bakover.common.infrastructure.job

import no.nav.su.se.bakover.common.domain.job.JobbKjøringRepo
import org.slf4j.LoggerFactory

/**
 * Holder en global referanse til [JobbKjøringRepo] slik at [startStoppableJob] automatisk
 * kan persistere kjørestatus uten at hver enkelt jobb trenger endring.
 *
 * Initialiseres ved oppstart (før jobber startes).
 */
object JobbKjøringPersistering {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var repo: JobbKjøringRepo? = null

    fun init(jobbKjøringRepo: JobbKjøringRepo) {
        repo = jobbKjøringRepo
        log.info("JobbKjøringPersistering initialisert.")
    }

    internal fun hentRepo(): JobbKjøringRepo? = repo
}
