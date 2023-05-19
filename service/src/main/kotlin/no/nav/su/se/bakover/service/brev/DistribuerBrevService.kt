package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
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
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class DistribuerBrevService(
    private val sakService: SakService,
    private val dokumentRepo: DokumentRepo,
    private val dokDistFordeling: DokDistFordeling,
    private val personService: PersonService,
    private val dokArkiv: DokArkiv,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun journalførOgDistribuerUtgåendeDokumenter() {
        dokumentRepo.hentDokumenterForDistribusjon()
            .map { dokumentdistribusjon ->
                val sak = sakService.hentSakInfo(dokumentdistribusjon.dokument.metadata.sakId).getOrElse {
                    throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${dokumentdistribusjon.dokument.metadata.sakId}")
                }
                journalførDokument(dokumentdistribusjon, sak.saksnummer, sak.type, sak.fnr)
                    .mapLeft {
                        Distribusjonsresultat.Feil.Journalføring(dokumentdistribusjon.id)
                    }
                    .flatMap { journalført ->
                        distribuerDokument(journalført)
                            .mapLeft {
                                Distribusjonsresultat.Feil.Brevdistribusjon(journalført.id)
                            }
                            .map { distribuert ->
                                Distribusjonsresultat.Ok(distribuert.id)
                            }
                    }
            }.ifNotEmpty {
                val ok = this.ok()
                val feil = this.feil()
                if (feil.isEmpty()) {
                    log.info("Journalførte/distribuerte distribusjonsIDene: $ok")
                } else {
                    log.error("Kunne ikke journalføre/distribuere distribusjonsIDene: $feil. Disse gikk ok: $ok")
                }
            }
    }

    // Internal for testing.
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

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    // TODO: Flytt inn i egen service
    // Internal for testing.
    internal fun journalførDokument(
        dokumentdistribusjon: Dokumentdistribusjon,
        saksnummer: Saksnummer,
        sakstype: Sakstype,
        fnr: Fnr,
    ): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val person = personService.hentPersonMedSystembruker(fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostFactory.lagJournalpost(
                    person = person,
                    saksnummer = saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sakstype,
                ),
            ).mapLeft {
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring
            }
        }.mapLeft {
            when (it) {
                is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> return dokumentdistribusjon.right()
                KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
            }
        }.map {
            dokumentRepo.oppdaterDokumentdistribusjon(it)
            it
        }
    }

    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }
}

private sealed class Distribusjonsresultat {
    sealed class Feil {
        data class Journalføring(val id: UUID) : Feil()
        data class Brevdistribusjon(val id: UUID) : Feil()
    }

    data class Ok(val id: UUID) : Distribusjonsresultat()
}

private fun List<Either<Distribusjonsresultat.Feil, Distribusjonsresultat.Ok>>.ok() =
    this.filterIsInstance<Either.Right<Distribusjonsresultat.Ok>>()
        .map { it.value.id.toString() }

private fun List<Either<Distribusjonsresultat.Feil, Distribusjonsresultat.Ok>>.feil() =
    this.filterIsInstance<Either.Left<Distribusjonsresultat.Feil>>()
        .map { it.value }
