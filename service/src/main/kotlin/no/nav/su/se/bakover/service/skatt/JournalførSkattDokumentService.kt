package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import dokument.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentPåSakCommand
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentPåSakCommand.Companion.lagJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalførSkattedokumentUtenforSakCommand
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.JournalførSkattedokumentPåSakClient
import no.nav.su.se.bakover.domain.skatt.JournalførSkattedokumentUtenforSakClient
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
    private val journalførSkattedokumentPåSakClient: JournalførSkattedokumentPåSakClient,
    private val journalførSkattedokumentUtenforSakClient: JournalførSkattedokumentUtenforSakClient,
    private val sakService: SakService,
    private val dokumentSkattRepo: DokumentSkattRepo,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Ment å bli kallt fra en jobb
     */
    fun journalførAlleSkattedokumenterPåSak(): List<JournalføringOgDistribueringsResultat> {
        return dokumentSkattRepo.hentDokumenterForJournalføring()
            .map { skattedokument -> journalførSkattedokumentPåSak(skattedokument).tilResultat(skattedokument, log) }
            .also { it.logResultat("Journalføring skatt", log) }
    }

    private fun journalførSkattedokumentPåSak(skattedokument: Skattedokument.Generert): Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført> {
        val sakInfo = sakService.hentSakInfo(skattedokument.sakid).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${skattedokument.sakid}")
        }
        return journalfør(skattedokument.lagJournalpost(sakInfo)).map {
            val tilJournalført = skattedokument.tilJournalført(it)
            dokumentSkattRepo.lagre(tilJournalført)
            tilJournalført
        }
    }

    fun journalfør(journalpost: JournalførSkattedokumentPåSakCommand): Either<KunneIkkeJournalføreDokument, JournalpostId> {
        return journalførSkattedokumentPåSakClient.journalførSkattedokument(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
    }

    fun journalfør(journalpost: JournalførSkattedokumentUtenforSakCommand): Either<KunneIkkeJournalføreDokument, JournalpostId> {
        return journalførSkattedokumentUtenforSakClient.journalførSkattedokument(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
    }
}
