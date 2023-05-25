package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import java.time.Clock
import java.util.UUID

class SkattDokumentServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokumentSkattRepo: DokumentSkattRepo,
    private val clock: Clock,
) : SkattDokumentService {

    override fun genererOgLagre(
        vedtak: Stønadsvedtak,
        txc: TransactionContext,
    ): Either<KunneIkkeGenerereSkattedokument, Skattedokument> = generer(vedtak).onRight { lagre(it, txc) }

    private fun generer(vedtak: Stønadsvedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument> {
        val hentetSkatt = when (vedtak.behandling.eksterneGrunnlag.skatt) {
            is EksterneGrunnlagSkatt.Hentet -> vedtak.behandling.eksterneGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet
            EksterneGrunnlagSkatt.IkkeHentet -> return KunneIkkeGenerereSkattedokument.SkattegrunnlagErIkkeHentetForÅGenereDokument.left()
        }

        val søkersSamletSkattegrunnlag = hentetSkatt.søkers.årsgrunnlag.mapNotNull {
            when (it.oppslag) {
                is Either.Left -> null
                is Either.Right -> it
            }
        }

        val epsSamletSkattegrunnlag = hentetSkatt.eps?.let {
            it.årsgrunnlag.mapNotNull {
                when (it.oppslag) {
                    is Either.Left -> null
                    is Either.Right -> it
                }
            }
        }

        if (søkersSamletSkattegrunnlag.isEmpty() && epsSamletSkattegrunnlag.isNullOrEmpty()) {
            return KunneIkkeGenerereSkattedokument.IngenÅrsgrunnlag.left()
        }

        return PdfInnhold.SkattemeldingsPdf.lagSkattemeldingsPdf(
            saksnummer = vedtak.saksnummer,
            søknadsbehandlingsId = vedtak.behandling.id,
            vedtaksId = vedtak.id,
            // vi henter skattemeldingene samtidig
            hentet = hentetSkatt.søkers.hentetTidspunkt,
            skatt = PdfInnhold.SkattemeldingsPdf.ÅrsgrunnlagForPdf(
                søkers = if (søkersSamletSkattegrunnlag.isEmpty()) {
                    null
                } else PdfInnhold.SkattemeldingsPdf.ÅrsgrunnlagMedFnr(
                    fnr = hentetSkatt.søkers.fnr,
                    årsgrunnlag = søkersSamletSkattegrunnlag.toNonEmptyList(),
                ),
                eps = if (hentetSkatt.eps == null || epsSamletSkattegrunnlag.isNullOrEmpty()) {
                    null
                } else
                    PdfInnhold.SkattemeldingsPdf.ÅrsgrunnlagMedFnr(
                        fnr = hentetSkatt.eps!!.fnr,
                        årsgrunnlag = epsSamletSkattegrunnlag.toNonEmptyList(),
                    ),
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
                }.let { PdfA(it) },
                dokumentJson = skattemeldingsPdf.toJson(),
            ).right()
        }
    }

    override fun lagre(skattedokument: Skattedokument) {
        dokumentSkattRepo.lagre(skattedokument)
    }

    override fun lagre(skattedokument: Skattedokument, txc: TransactionContext) {
        dokumentSkattRepo.lagre(skattedokument, txc)
    }
}
