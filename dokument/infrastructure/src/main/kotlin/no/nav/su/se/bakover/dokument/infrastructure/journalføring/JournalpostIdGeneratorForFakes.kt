package no.nav.su.se.bakover.dokument.infrastructure.journalføring

import no.nav.su.se.bakover.common.journal.JournalpostId
import java.util.concurrent.atomic.AtomicLong

/** Thread safe */
class JournalpostIdGeneratorForFakes {
    private var id = AtomicLong(0L)

    /** Thread safe */
    fun next() = JournalpostId(id.getAndIncrement().toString())
}
