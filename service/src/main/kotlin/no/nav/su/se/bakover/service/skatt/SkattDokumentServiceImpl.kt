package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagForPdf
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagMedFnr
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.journalpost.JournalpostSkattUtenforSak
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * Service som 'gjør ting' med dokumenter/pdf tilhørende skatt
 */
class SkattDokumentServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokumentSkattRepo: DokumentSkattRepo,
    private val journalførSkattDokumentService: JournalførSkattDokumentService,
    private val clock: Clock,
) : SkattDokumentService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun genererOgLagre(
        vedtak: Stønadsvedtak,
        txc: TransactionContext,
    ): Either<KunneIkkeGenerereSkattedokument, Skattedokument> = generer(vedtak).onRight { lagre(it, txc) }

    override fun genererSkattePdf(
        request: GenererSkattPdfRequest,
    ): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA> {
        return lagSkattePdfInnhold(
            fagsystemId = request.fagsystemId,
            sakstype = request.sakstype,
            begrunnelse = request.begrunnelse,
            skattegrunnlagSøker = request.skattegrunnlagSøkers,
            skattegrunnlagEps = request.skattegrunnlagEps,
        )
            .getOrElse { return it.left() }
            .let {
                pdfGenerator.genererPdf(it).getOrElse {
                    return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedPdfGenerering(it).left()
                }
            }.right()
    }

    override fun genererSkattePdfOgJournalfør(request: GenererSkattPdfRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA> {
        return lagSkattePdfInnhold(
            fagsystemId = request.fagsystemId,
            sakstype = request.sakstype,
            begrunnelse = request.begrunnelse,
            skattegrunnlagSøker = request.skattegrunnlagSøkers,
            skattegrunnlagEps = request.skattegrunnlagEps,
        )
            .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedGenereringAvPdf(it).left() }
            .let {
                val pdf = pdfGenerator.genererPdf(it).getOrElse {
                    return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedGenereringAvPdf(
                        KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedPdfGenerering(it),
                    ).left()
                }
                log.info("Genererte skatte-pdf for sakstype ${request.sakstype} med fagsystemId ${request.fagsystemId}")

                journalførSkattDokumentService.journalfør(
                    JournalpostSkattUtenforSak.tryCreate(
                        fnr = request.skattegrunnlagSøkers.fnr,
                        sakstype = request.sakstype,
                        fagsystemId = request.fagsystemId,
                        dokument = Dokument.UtenMetadata.Informasjon.Annet(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(clock),
                            tittel = it.pdfTemplate.tittel(),
                            generertDokument = pdf,
                            generertDokumentJson = it.toJson(),
                        ),
                    ).getOrElse {
                        return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalpostUtenforSak(it).left()
                    },
                ).mapLeft {
                    KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedJournalføring(it)
                }.map {
                    log.info("Journalførte skatte-pdf for sakstype ${request.sakstype} med fagsystemId ${request.fagsystemId}. Fikk journalpostId $it")
                    pdf
                }
            }
    }

    private fun lagSkattePdfInnhold(
        fagsystemId: String,
        sakstype: Sakstype,
        begrunnelse: String,
        skattegrunnlagSøker: Skattegrunnlag,
        skattegrunnlagEps: Skattegrunnlag?,
    ): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, SkattegrunnlagsPdfInnhold> {
        return SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdfInnholdFraFrioppslag(
            fagsystemId = fagsystemId,
            sakstype = sakstype,
            begrunnelse = begrunnelse,
            skattegrunnlagSøker = skattegrunnlagSøker,
            skattegrunnlagEps = skattegrunnlagEps,
            hentNavn = {
                personOppslag.person(it).map { it.navn }
            },
            clock = clock,
        ).map {
            it
        }.mapLeft {
            KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedHentingAvPerson(it)
        }
    }

    private fun generer(vedtak: Stønadsvedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument> {
        val hentetSkatt = when (vedtak.behandling.eksterneGrunnlag.skatt) {
            is EksterneGrunnlagSkatt.Hentet -> vedtak.behandling.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet
            EksterneGrunnlagSkatt.IkkeHentet -> return KunneIkkeGenerereSkattedokument.SkattegrunnlagErIkkeHentetForÅGenereDokument.left()
        }

        return SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
            saksnummer = vedtak.saksnummer,
            søknadsbehandlingsId = vedtak.behandling.id,
            vedtaksId = vedtak.id,
            // vi henter skattemeldingene samtidig
            hentet = hentetSkatt.søkers.hentetTidspunkt,
            skatt = ÅrsgrunnlagForPdf(
                søkers = ÅrsgrunnlagMedFnr(
                    fnr = hentetSkatt.søkers.fnr,
                    årsgrunnlag = hentetSkatt.søkers.årsgrunnlag,
                ),
                eps = if (hentetSkatt.eps == null) {
                    null
                } else {
                    ÅrsgrunnlagMedFnr(fnr = hentetSkatt.eps!!.fnr, årsgrunnlag = hentetSkatt.eps!!.årsgrunnlag)
                },
            ),
            hentNavn = { fnr ->
                personOppslag.person(fnr).getOrElse {
                    throw IllegalStateException("Feil ved henting av person. Denne var hentet for ikke så lenge siden. SkattDokumentServiceImpl.kt")
                }.navn
            },
            clock = clock,
        ).let { skattemeldingsPdf ->
            Skattedokument.Generert(
                id = UUID.randomUUID(),
                søkersSkatteId = hentetSkatt.søkers.id,
                epsSkatteId = hentetSkatt.eps?.id,
                sakid = vedtak.sakId,
                vedtakid = vedtak.id,
                generertDokument = pdfGenerator.genererPdf(skattemeldingsPdf).getOrElse {
                    return KunneIkkeGenerereSkattedokument.FeilVedGenereringAvDokument.left()
                },
                dokumentJson = skattemeldingsPdf.toJson(),
                skattedataHentet = hentetSkatt.søkers.hentetTidspunkt,
            ).right()
        }
    }

    override fun lagre(skattedokument: Skattedokument, txc: TransactionContext) {
        dokumentSkattRepo.lagre(skattedokument, txc)
    }
}
