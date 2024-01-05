package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev
import dokument.domain.brev.KunneIkkeJournalføreBrev
import dokument.domain.brev.KunneIkkeJournalføreDokument
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.brev.JournalførBrevCommand
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.logResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * journalfører 'vanlige' dokumenter (f.eks vedtak). Ment å bli kallt fra en jobb
 */
class JournalførDokumentService(
    private val journalførBrevClient: JournalførBrevClient,
    private val dokumentRepo: DokumentRepo,
    private val sakService: SakService,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun journalfør(): List<JournalføringOgDistribueringsResultat> = dokumentRepo.hentDokumenterForJournalføring()
        .map { dokumentdistribusjon -> journalførDokument(dokumentdistribusjon).tilResultat(dokumentdistribusjon, log) }
        .also { it.logResultat("Journalfør dokument", log) }

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    private fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sakInfo = sakService.hentSakInfo(dokumentdistribusjon.dokument.metadata.sakId).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${dokumentdistribusjon.dokument.metadata.sakId}")
        }
        val journalførtDokument = dokumentdistribusjon.journalfør {
            journalfør(
                command = JournalførBrevCommand(
                    saksnummer = sakInfo.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sakInfo.type,
                    fnr = sakInfo.fnr,
                ),
            ).mapLeft { KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring }
        }

        return journalførtDokument
            .mapLeft {
                when (it) {
                    is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> return dokumentdistribusjon.right()
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
                }
            }
            .onRight {
                dokumentRepo.oppdaterDokumentdistribusjon(it)
            }
    }

    private fun journalfør(command: JournalførBrevCommand): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        return journalførBrevClient.journalførBrev(command)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }
}
