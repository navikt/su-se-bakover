package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold.Companion.lagPdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import java.time.Clock
import java.time.Year
import java.util.UUID

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val personService: PersonService,
    private val pdfGenerator: PdfGenerator,
    val clock: Clock,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock).minusYears(1))
            .hentMestGyldigeSkattegrunnlag(),
        årSpurtFor = Year.now(clock).minusYears(1).toRange(),
    )

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        yearRange: YearRange,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlagForÅrsperiode(fnr, yearRange)
            .map { it.hentMestGyldigeSkattegrunnlag() }.toNonEmptyList(),
        årSpurtFor = yearRange,
    )

    override fun hentOgLagPdfAvSamletSkattegrunnlagFor(
        request: FrioppslagSkattRequest,
    ): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA> {
        val skattegrunnlag = Skattegrunnlag(
            id = UUID.randomUUID(),
            fnr = request.fnr,
            hentetTidspunkt = Tidspunkt.now(clock),
            saksbehandler = request.saksbehandler,
            årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.fnr, request.år)
                .hentMestGyldigeSkattegrunnlagEllerFeil()
                .getOrElse { return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.KunneIkkeHenteSkattemelding(it).left() },
            årSpurtFor = request.år.toRange(),
        )

        val skattPdfInnhold = skattegrunnlag.lagPdfInnhold(
            saksnummer = request.saksnummer,
            begrunnelse = request.begrunnelse,
            navn = personService.hentPerson(request.fnr)
                .getOrElse { return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedHentingAvPerson(it).left() }.navn,
            clock = clock,
        )

        return pdfGenerator.genererPdf(skattPdfInnhold).getOrElse {
            return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedPdfGenerering(it).left()
        }.right()
    }
}
