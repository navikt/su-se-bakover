package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock

/**
 * TODO jah: Prøve å finne skillet/abstraksjonen mellom brev og dokument
 *  For domenet er brev mer spesifikt/beskrivende. Samtidig synes jeg det er helt greit å ha en litt bredere abstraksjon i persisteringslaget/klientlaget (Dokument)
 */
class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val dokumentRepo: DokumentRepo,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    private val microsoftGraphApiOppslag: IdentClient,
    private val utbetalingService: UtbetalingService,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
) : BrevService {

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

    override fun lagreDokument(dokument: Dokument.MedMetadata) {
        sessionFactory.withTransactionContext {
            lagreDokument(dokument, it)
        }
    }

    override fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext) {
        dokumentRepo.lagre(dokument, transactionContext)
    }

    override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
        return when (hentDokumenterForIdType) {
            is HentDokumenterForIdType.Sak -> dokumentRepo.hentForSak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Søknad -> dokumentRepo.hentForSøknad(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Revurdering -> dokumentRepo.hentForRevurdering(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Vedtak -> dokumentRepo.hentForVedtak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.Klage -> dokumentRepo.hentForKlage(hentDokumenterForIdType.id)
        }
    }

    private fun lagPdf(brevInnhold: BrevInnhold): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevInnhold).mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }.map { it }
    }

    override fun lagDokument(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return lagBrevRequest(visitable).mapLeft {
            when (it) {
                is KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling
                is KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                is KunneIkkeLageBrevRequest.KunneIkkeHentePerson -> KunneIkkeLageDokument.KunneIkkeHentePerson
                is KunneIkkeLageBrevRequest.SkalIkkeSendeBrev -> KunneIkkeLageDokument.DetSkalIkkeSendesBrev
            }
        }.flatMap { lagBrevRequest ->
            lagDokument(lagBrevRequest)
        }
    }

    override fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeLageBrevRequest, LagBrevRequest> {
        return lagBrevRequestVisitor().apply {
            visitable.accept(this)
        }.brevRequest
    }

    private fun lagBrevRequestVisitor() = LagBrevRequestVisitor(
        hentPerson = { fnr ->
            /** [no.nav.su.se.bakover.web.services.AccessCheckProxy] bør allerede ha sjekket om vi har tilgang til personen */
            personService.hentPersonMedSystembruker(fnr).mapLeft { KunneIkkeLageBrevRequest.KunneIkkeHentePerson }
        },
        hentNavn = { ident ->
            microsoftGraphApiOppslag.hentNavnForNavIdent(ident)
                .mapLeft { KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(it) }
        },
        hentGjeldendeUtbetaling = { sakId, forDato ->
            utbetalingService.hentGjeldendeUtbetaling(sakId, forDato)
                .map { it.beløp }
                .mapLeft { KunneIkkeLageBrevRequest.KunneIkkeFinneGjeldendeUtbetaling }
        },
        clock = clock,
        satsFactory = satsFactory,
    )
}
