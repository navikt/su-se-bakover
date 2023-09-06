package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pdf.SamletÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.SkattegrunnlagPdfTemplate
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
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
    val søkers: SkattPdfDataJson,
    val eps: SkattPdfDataJson?,
    val begrunnelse: String?,
) : PdfInnhold() {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = SkattegrunnlagPdfTemplate

    companion object {
        fun lagSkattegrunnlagsPdf(
            saksnummer: Saksnummer,
            søknadsbehandlingsId: UUID,
            vedtaksId: UUID,
            hentet: Tidspunkt,
            skatt: ÅrsgrunnlagForPdf,
            hentNavn: (Fnr) -> Person.Navn,
            clock: Clock,
        ): SkattegrunnlagsPdfInnhold = SkattegrunnlagsPdfInnhold(
            saksnummer = saksnummer.toString(),
            behandlingstype = BehandlingstypeForSkattemelding.Søknadsbehandling,
            sakstype = Sakstype.UFØRE,
            behandlingsId = søknadsbehandlingsId,
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
            skattegrunnlagSøker: Skattegrunnlag,
            skattegrunnlagEps: Skattegrunnlag?,
            hentNavn: (Fnr) -> Either<KunneIkkeHentePerson, Person.Navn>,
            clock: Clock,
        ): Either<KunneIkkeHentePerson, SkattegrunnlagsPdfInnhold> = SkattegrunnlagsPdfInnhold(
            saksnummer = fagsystemId,
            behandlingstype = BehandlingstypeForSkattemelding.Frioppslag,
            sakstype = sakstype,
            behandlingsId = null,
            vedtaksId = null,
            hentet = skattegrunnlagSøker.hentetTidspunkt,
            opprettet = Tidspunkt.now(clock),
            søkers = SkattPdfDataJson(
                fnr = skattegrunnlagSøker.fnr,
                navn = hentNavn(skattegrunnlagSøker.fnr).getOrElse { return it.left() },
                årsgrunnlag = skattegrunnlagSøker.årsgrunnlag.tilPdfJson(),
            ),
            eps = if (skattegrunnlagEps != null) {
                SkattPdfDataJson(
                    fnr = skattegrunnlagEps.fnr,
                    navn = hentNavn(skattegrunnlagEps.fnr).getOrElse { return it.left() },
                    årsgrunnlag = skattegrunnlagEps.årsgrunnlag.tilPdfJson(),
                )
            } else {
                null
            },
            begrunnelse = begrunnelse,
        ).right()
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
