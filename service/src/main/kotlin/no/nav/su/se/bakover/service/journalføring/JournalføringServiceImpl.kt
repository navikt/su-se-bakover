package no.nav.su.se.bakover.service.journalføring;

import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService

/**
 * Wrapper klasse som kaller journalfør() for alle klasser
 */
class JournalføringServiceImpl(
    private val journalførDokumentService: JournalførDokumentService,
    private val journalførSkattDokumentService: JournalførSkattDokumentService,
) : JournalføringService {
    override fun journalfør() {
        journalførDokumentService.journalfør()
        journalførSkattDokumentService.journalfør()
    }
}
