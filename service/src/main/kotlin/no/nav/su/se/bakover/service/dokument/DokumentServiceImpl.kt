package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokarkiv.lagJournalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeBestilleBrevForDokument
import no.nav.su.se.bakover.domain.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.dokument.DistribueringsResultat.Companion.feil
import no.nav.su.se.bakover.service.dokument.DistribueringsResultat.Companion.ok
import no.nav.su.se.bakover.service.dokument.JournalføringsResultat.Companion.feil
import no.nav.su.se.bakover.service.dokument.JournalføringsResultat.Companion.ok
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DokumentServiceImpl(
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val sakService: SakService,
    private val personService: PersonService,
    private val dokumentRepo: DokumentRepo,
    private val dokumentSkattRepo: DokumentSkattRepo,
) : DokumentService {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun journalførDokumenter() {
        val dokumenterSomMåJournalføres = dokumentRepo.hentDokumenterForJournalføring()
        val skatteDokumenterSomMåJournalføres = dokumentSkattRepo.hentDokumenterForJournalføring()

        val dokumenterResultat = dokumenterSomMåJournalføres.map { dokumentdistribusjon ->
            journalførDokument(dokumentdistribusjon)
                .map { JournalføringsResultat.Ok(dokumentdistribusjon.id) }
                .mapLeft { JournalføringsResultat.Feil(dokumentdistribusjon.id) }
        }

        val skatteDokumenterResultat = skatteDokumenterSomMåJournalføres.map { skattedokument ->
            journalførSkattedokument(skattedokument)
                .map { JournalføringsResultat.Ok(it.id) }
                .mapLeft { JournalføringsResultat.Feil(skattedokument.id) }
        }

        val resultat = dokumenterResultat + skatteDokumenterResultat
        resultat.ifNotEmpty {
            val ok = this.ok()
            val feil = this.feil()
            if (feil.isEmpty()) {
                log.info("Journalførte: $ok")
            } else {
                log.error("Kunne ikke journalføre: $feil. Disse gikk ok: $ok")
            }
        }
    }

    override fun distribuer() {
        dokumentRepo.hentDokumenterForDistribusjon().map { dokument ->
            distribuerDokument(dokument)
                .map { DistribueringsResultat.Ok(it.id) }
                .mapLeft { DistribueringsResultat.Feil(dokument.id) }
        }.ifNotEmpty {
            val ok = this.ok()
            val feil = this.feil()
            if (feil.isEmpty()) {
                log.info("distribuerte: $ok")
            } else {
                log.error("Kunne ikke distribuere: $feil. Disse gikk ok: $ok")
            }
        }
    }


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

    // Internal for testing.
    internal fun distribuerBrev(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeDistribuereBrev, BrevbestillingId> =
        dokDistFordeling.bestillDistribusjon(journalpostId, distribusjonstype, distribusjonstidspunkt)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }


    private fun journalførSkattedokument(skattedokument: Skattedokument.Generert): Either<KunneIkkeJournalføreDokument, Skattedokument.Journalført> {
        val sakInfo = sakService.hentSakInfo(skattedokument.sakid).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${skattedokument.sakid}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return journalfør(skattedokument.lagJournalpost(sakInfo, person))
            .mapLeft { KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost }
            .map {
                val tilJournalført = skattedokument.tilJournalført(it)
                dokumentSkattRepo.lagre(tilJournalført)
                tilJournalført
            }
    }

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    // TODO: Flytt inn i egen service
    // Internal for testing.
    internal fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sakInfo = sakService.hentSakInfo(dokumentdistribusjon.dokument.metadata.sakId).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${dokumentdistribusjon.dokument.metadata.sakId}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        val journalførtDokument = dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostFactory.lagJournalpost(
                    person = person,
                    saksnummer = sakInfo.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sakInfo.type,
                ),
            ).mapLeft { KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring }
        }

        return journalførtDokument
            .mapLeft { KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost }
            .onRight {
                dokumentRepo.oppdaterDokumentdistribusjon(it)
            }
    }


    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }


}
