package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.brev.FantIkkeDokument
import dokument.domain.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.brev.dokumentMapper.tilDokument
import no.nav.su.se.bakover.domain.brev.jsonRequest.tilPdfInnhold
import person.domain.IdentClient
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

/**
 * TODO jah: Prøve å finne skillet/abstraksjonen mellom brev og dokument
 *  For domenet er brev mer spesifikt/beskrivende. Samtidig synes jeg det er helt greit å ha en litt bredere abstraksjon i persisteringslaget/klientlaget (Dokument)
 */
class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val dokumentRepo: DokumentRepo,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    private val identClient: IdentClient,
    private val clock: Clock,
) : BrevService {

    override fun lagDokument(
        command: GenererDokumentCommand,
        id: UUID,
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return command.tilPdfInnhold(
            clock = clock,
            // TODO jah: Både saksbehandlere/attestanter/systemet må kunne generere dokumenter. Skal vi holde oss til systembruker her? Eller bør vi lage en egen funksjon for å hente person uten systembruker?
            hentPerson = { personService.hentPersonMedSystembruker(command.fødselsnummer) },
            hentNavnForIdent = identClient::hentNavnForNavIdent,
        ).mapLeft { KunneIkkeLageDokument.FeilVedHentingAvInformasjon }
            .flatMap { pdfInnhold ->
                pdfGenerator.genererPdf(pdfInnhold).mapLeft { KunneIkkeLageDokument.FeilVedGenereringAvPdf }
                    .map { pdfA ->
                        Pair(pdfA, pdfInnhold)
                    }
            }
            .map { (pdfA, pdfInnhold) ->
                pdfA.tilDokument(
                    id = id,
                    pdfInnhold = pdfInnhold,
                    command = command,
                    clock = clock,
                )
            }
    }

    override fun hentDokument(id: UUID): Either<FantIkkeDokument, Dokument.MedMetadata> {
        return dokumentRepo.hentDokument(id)?.right() ?: FantIkkeDokument.left()
    }

    override fun lagreDokument(
        dokument: Dokument.MedMetadata,
        transactionContext: TransactionContext?,
    ) {
        dokumentRepo.lagre(dokument, transactionContext)
    }

    override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
        return when (hentDokumenterForIdType) {
            is HentDokumenterForIdType.HentDokumenterForSak -> dokumentRepo.hentForSak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.HentDokumenterForSøknad -> dokumentRepo.hentForSøknad(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.HentDokumenterForRevurdering -> dokumentRepo.hentForRevurdering(
                hentDokumenterForIdType.id,
            )

            is HentDokumenterForIdType.HentDokumenterForVedtak -> dokumentRepo.hentForVedtak(hentDokumenterForIdType.id)
            is HentDokumenterForIdType.HentDokumenterForKlage -> dokumentRepo.hentForKlage(hentDokumenterForIdType.id)
        }
    }
}
