package no.nav.su.se.bakover.service.journalføring

import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService

/**
 * Wrapper klasse som kaller journalfør() for alle klasser. ment for å bli kallt fra en jobb.
 */
class JournalføringService(
    private val journalførDokumentService: JournalførDokumentService,
    private val journalførSkattDokumentService: JournalførSkattDokumentService,
) {
    fun journalfør() {
        journalførDokumentService.journalfør()
        journalførSkattDokumentService.journalførAlleSkattedokumenterPåSak()
    }
}
