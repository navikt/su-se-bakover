package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.Distribusjonstidspunkt
import no.nav.su.se.bakover.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * TODO jah: Prøve å finne skillet/abstraksjonen mellom brev og dokument
 *  For domenet er brev mer spesifikt/beskrivende. Samtidig synes jeg det er helt greit å ha en litt bredere abstraksjon i persisteringslaget/klientlaget (Dokument)
 */
internal class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val dokumentRepo: DokumentRepo,
    private val sakService: SakService,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    private val microsoftGraphApiOppslag: IdentClient,
    private val utbetalingService: UtbetalingService,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : BrevService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        return lagPdf(request.brevInnhold)
    }

    override fun lagDokument(request: LagBrevRequest): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return request.tilDokument(clock) {
            lagBrev(it).mapLeft {
                LagBrevRequest.KunneIkkeGenererePdf
            }
        }.mapLeft {
            KunneIkkeLageDokument.KunneIkkeGenererePDF
        }
    }

    private fun journalfør(journalpost: Journalpost): Either<KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
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

    override fun lagreDokument(dokument: Dokument.MedMetadata) {
        sessionFactory.withTransactionContext {
            lagreDokument(dokument, it)
        }
    }

    override fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext) {
        dokumentRepo.lagre(dokument, transactionContext)
    }

    override fun journalførOgDistribuerUtgåendeDokumenter() {
        dokumentRepo.hentDokumenterForDistribusjon()
            .map { dokumentdistribusjon ->
                journalførDokument(dokumentdistribusjon)
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

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    // Internal for testing.
    internal fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sak = sakService.hentSak(dokumentdistribusjon.dokument.metadata.sakId)
            .getOrHandle { return KunneIkkeJournalføreDokument.KunneIkkeFinneSak.left() }
        val person = personService.hentPersonMedSystembruker(sak.fnr)
            .getOrHandle { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        return dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostFactory.lagJournalpost(
                    person = person,
                    saksnummer = sak.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sak.type,
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

    override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
        return when (hentDokumenterForIdType) {
            is HentDokumenterForIdType.Sak -> dokumentRepo.hentForSak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Søknad -> dokumentRepo.hentForSøknad(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Revurdering -> dokumentRepo.hentForRevurdering(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Vedtak -> dokumentRepo.hentForVedtak(hentDokumenterForIdType.id)
        }
    }

    private fun lagPdf(brevInnhold: BrevInnhold): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevInnhold)
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }
            .map { it }
    }

    override fun lagDokument(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return lagBrevRequest(visitable).mapLeft {
            when (it) {
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeLageDokument.KunneIkkeHentePerson
            }
        }.flatMap { lagBrevRequest ->
            lagDokument(lagBrevRequest)
        }
    }

    override fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest, LagBrevRequest> {
        return LagBrevRequestVisitor().apply {
            visitable.accept(this)
        }.brevRequest
    }

    private fun LagBrevRequestVisitor() =
        LagBrevRequestVisitor(
            hentPerson = { fnr ->
                /** [no.nav.su.se.bakover.service.AccessCheckProxy] bør allerede ha sjekket om vi har tilgang til personen */
                personService.hentPersonMedSystembruker(fnr)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
            },
            hentNavn = { ident ->
                microsoftGraphApiOppslag.hentNavnForNavIdent(ident)
                    .mapLeft { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant }
            },
            hentGjeldendeUtbetaling = { sakId, forDato ->
                utbetalingService.hentGjeldendeUtbetaling(sakId, forDato)
                    .bimap(
                        { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling },
                        { it.beløp },
                    )
            },
            clock = clock,
            satsFactory = satsFactory,
        )
}
