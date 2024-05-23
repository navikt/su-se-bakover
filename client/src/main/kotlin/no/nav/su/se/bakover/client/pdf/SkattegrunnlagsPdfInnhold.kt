package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.SkattegrunnlagPdfTemplate
import no.nav.su.se.bakover.client.pdf.SamletÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skattegrunnlag
import java.time.Clock
import java.util.UUID

data class SkattegrunnlagsPdfInnhold private constructor(
    val saksnummer: String?,
    val behandlingstype: BehandlingstypeForSkattemelding,
    override val sakstype: Sakstype,
    val behandlingsId: UUID?,
    val vedtaksId: UUID?,
    val hentet: Tidspunkt,
    val opprettet: Tidspunkt,
    val søkers: SkattPdfDataJson?,
    val eps: SkattPdfDataJson?,
    val begrunnelse: String?,
) : PdfInnhold {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = SkattegrunnlagPdfTemplate

    companion object {
        fun lagSkattegrunnlagsPdf(
            saksnummer: Saksnummer,
            søknadsbehandlingId: SøknadsbehandlingId,
            vedtaksId: UUID,
            hentet: Tidspunkt,
            skatt: ÅrsgrunnlagForPdf,
            hentNavn: (Fnr) -> Person.Navn,
            clock: Clock,
        ): SkattegrunnlagsPdfInnhold = SkattegrunnlagsPdfInnhold(
            saksnummer = saksnummer.toString(),
            behandlingstype = BehandlingstypeForSkattemelding.Søknadsbehandling,
            sakstype = Sakstype.UFØRE,
            behandlingsId = søknadsbehandlingId.value,
            vedtaksId = vedtaksId,
            hentet = hentet,
            opprettet = Tidspunkt.now(clock),
            søkers = SkattPdfDataJson(
                skatt.søkers.fnr,
                hentNavn(skatt.søkers.fnr),
                skatt.søkers.årsgrunnlag.tilPdfJson(),
            ),
            eps = skatt.eps?.let { SkattPdfDataJson(it.fnr, hentNavn(it.fnr), it.årsgrunnlag.tilPdfJson()) },
            begrunnelse = null,
        )

        fun lagSkattegrunnlagsPdfInnholdFraFrioppslag(
            fagsystemId: String,
            sakstype: Sakstype,
            begrunnelse: String?,
            skattegrunnlagSøker: Skattegrunnlag?,
            skattegrunnlagEps: Skattegrunnlag?,
            hentNavn: (Fnr) -> Either<KunneIkkeHentePerson, Person.Navn>,
            clock: Clock,
        ): Either<KunneIkkeHentePerson, SkattegrunnlagsPdfInnhold> {
            if (skattegrunnlagSøker == null && skattegrunnlagEps == null) {
                throw IllegalArgumentException("Skattegrunnlag for søker og eps kan ikke være null samtidig")
            }

            return SkattegrunnlagsPdfInnhold(
                saksnummer = fagsystemId,
                behandlingstype = BehandlingstypeForSkattemelding.Frioppslag,
                sakstype = sakstype,
                behandlingsId = null,
                vedtaksId = null,
                hentet = skattegrunnlagSøker?.hentetTidspunkt ?: skattegrunnlagEps!!.hentetTidspunkt,
                opprettet = Tidspunkt.now(clock),
                søkers = skattegrunnlagSøker?.let {
                    SkattPdfDataJson(
                        fnr = it.fnr,
                        navn = hentNavn(it.fnr).getOrElse { return it.left() },
                        årsgrunnlag = it.årsgrunnlag.tilPdfJson(),
                    )
                },
                eps = skattegrunnlagEps?.let {
                    SkattPdfDataJson(
                        fnr = it.fnr,
                        navn = hentNavn(it.fnr).getOrElse { return it.left() },
                        årsgrunnlag = it.årsgrunnlag.tilPdfJson(),
                    )
                },
                begrunnelse = begrunnelse,
            ).right()
        }
    }
}

enum class BehandlingstypeForSkattemelding {
    Søknadsbehandling,
    Frioppslag,
}

data class ÅrsgrunnlagMedFnr(
    val fnr: Fnr,
    val årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>,
)

data class ÅrsgrunnlagForPdf(
    val søkers: ÅrsgrunnlagMedFnr,
    val eps: ÅrsgrunnlagMedFnr?,
)
