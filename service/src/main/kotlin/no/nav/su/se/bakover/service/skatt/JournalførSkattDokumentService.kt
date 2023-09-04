package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand
import no.nav.su.se.bakover.domain.journalpost.JournalpostSkattForSak.Companion.lagJournalpost
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.logResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * journalfører dokumenter/pdf tilhørende skatt
 */
class JournalførSkattDokumentService(
    private val dokArkiv: DokArkiv,
    private val sakService: SakService,
    private val dokumentSkattRepo: DokumentSkattRepo,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Ment å bli kallt fra en jobb
     */
    fun journalførAlleSkattedokumenter(): List<JournalføringOgDistribueringsResultat> {
        return dokumentSkattRepo.hentDokumenterForJournalføring()
            .map { skattedokument -> journalførSkattedokument(skattedokument).tilResultat(skattedokument, log) }
            .also { it.logResultat("Journalføring skatt", log) }
    }

    private fun journalførSkattedokument(skattedokument: Skattedokument.Generert): Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført> {
        val sakInfo = sakService.hentSakInfo(skattedokument.sakid).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${skattedokument.sakid}")
        }
        return journalfør(skattedokument.lagJournalpost(sakInfo)).map {
            val tilJournalført = skattedokument.tilJournalført(it)
            dokumentSkattRepo.lagre(tilJournalført)
            tilJournalført
        }
    }

    fun journalfør(journalpost: JournalpostCommand): Either<KunneIkkeJournalføreDokument, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
    }
}
