package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.service.dokument.DokumentResultatSet.Companion.logResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DistribuerDokumentServiceImpl(
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
) : DistribuerDokumentService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun distribuer() {
        dokumentRepo.hentDokumenterForDistribusjon().map { dokument ->
            distribuerDokument(dokument)
                .map { DokumentResultatSet.Ok(it.id) }
                .mapLeft {
                    log.error(
                        "Kunne ikke distribuere brev med dokumentid ${dokument.id} og journalpostid ${dokument.journalføringOgBrevdistribusjon.journalpostId()}: $it",
                        RuntimeException("Genererer en stacktrace for enklere debugging."),
                    )
                    DokumentResultatSet.Feil(dokument.id)
                }
        }.logResultat("Distribuering", log)
    }

    /**
     * Internal for testing.
     * sikkert fordi man ikke vil skrive så mye :shrug: kan bli gjort private hvis man tester mulige feil-caser
     */
    internal fun distribuerDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeBestilleBrevForDokument, Dokumentdistribusjon> {
        return dokumentdistribusjon.distribuerBrev { jounalpostId ->
            distribuerBrev(
                jounalpostId,
                dokumentdistribusjon.dokument.distribusjonstype,
                dokumentdistribusjon.dokument.distribusjonstidspunkt,
            )
                .mapLeft {
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(
                        journalpostId = jounalpostId,
                    )
                }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.AlleredeDistribuertBrev -> return dokumentdistribusjon.right()
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev -> KunneIkkeBestilleBrevForDokument.FeilVedBestillingAvBrev
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.MåJournalføresFørst -> KunneIkkeBestilleBrevForDokument.MåJournalføresFørst
            }
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it
        }
    }

    /**
     * Internal for testing.
     * sikkert fordi man ikke vil skrive så mye :shrug: kan bli gjort private hvis man tester mulige feil-caser
     */
    internal fun distribuerBrev(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeDistribuereBrev, BrevbestillingId> {
        return dokDistFordeling.bestillDistribusjon(journalpostId, distribusjonstype, distribusjonstidspunkt)
            .mapLeft {
                log.error(
                    "Feil ved bestilling av distribusjon for journalpostId: $journalpostId, distribusjonstype: $distribusjonstype, distribusjonstidspunkt: $distribusjonstidspunkt. Feilen var: $it",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
                KunneIkkeDistribuereBrev
            }
    }
}
