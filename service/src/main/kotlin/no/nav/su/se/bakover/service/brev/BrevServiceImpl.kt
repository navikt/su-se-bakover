package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentPdf
import dokument.domain.DokumentRepo
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.PdfGenerator
import dokument.domain.brev.BrevService
import dokument.domain.brev.FantIkkeDokument
import dokument.domain.brev.HentDokumenterForIdType
import dokument.domain.forsteside.ForstesideGeneratorService
import dokument.domain.journalføring.tilBehandlingstema
import dokument.domain.pdf.SammenslåPdf
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.TransactionContext
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
    private val identClient: IdentClient,
    private val clock: Clock,
    private val forstesideGeneratorService: ForstesideGeneratorService,
) : BrevService {

    // basert på kommandotypen så avjøres typen av Metadata
    override fun lagDokumentPdf(
        command: GenererDokumentCommand,
        id: UUID,
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return command.tilPdfInnhold(
            clock = clock,
            // TODO jah: Både saksbehandlere/attestanter/systemet må kunne generere dokumenter. Skal vi holde oss til systembruker her? Eller bør vi lage en egen funksjon for å hente person uten systembruker?
            hentPerson = { personService.hentPersonMedSystembruker(command.fødselsnummer, command.sakstype) },
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

    override fun lagDokumentMedForsideSomVedlegg(
        command: GenererDokumentCommand,
        id: UUID,
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
        return lagDokumentPdf(
            command = command,
            id = id,
        ).flatMap { dokument ->
            val førsteside = when (command.sakstype) {
                Sakstype.ALDER ->
                    forstesideGeneratorService.genererForsideForEttersendingAlder(
                        brukerId = command.fødselsnummer.toString(),
                        behandlingstema = command.sakstype.tilBehandlingstema(),
                    )

                Sakstype.UFØRE ->
                    forstesideGeneratorService.genererForsideForEttersendingUføre(
                        brukerId = command.fødselsnummer.toString(),
                        behandlingstema = command.sakstype.tilBehandlingstema(),
                    )
            }
            førsteside
                .mapLeft {
                    KunneIkkeLageDokument.FeilVedGenereringAvForside
                }
                .flatMap { response ->
                    SammenslåPdf.leggTilVedlegg(
                        dokument = dokument.generertDokument,
                        vedlegg = response.foersteside,
                    ).mapLeft {
                        KunneIkkeLageDokument.FeilVedSammenslåingAvPdfOgVedlegg
                    }.map { sammenslåttPdf ->
                        when (dokument) {
                            is Dokument.UtenMetadata.Informasjon ->
                                dokument.medGenerertDokument(sammenslåttPdf)

                            else -> dokument
                        }
                    }
                }
        }
    }

    override fun hentDokumentPdf(id: UUID): Either<FantIkkeDokument, DokumentPdf> {
        return dokumentRepo.hentDokumentPdf(id)?.right() ?: FantIkkeDokument.left()
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
            is HentDokumenterForIdType.HentDokumenterForHistoriskInfotrygdRevurdering -> dokumentRepo.hentForHistoriskRevurdering(
                hentDokumenterForIdType.id,
            )
            is HentDokumenterForIdType.HentDokumenterForKlage -> dokumentRepo.hentForKlage(hentDokumenterForIdType.id)
        }
    }
}
