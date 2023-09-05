package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Year
import java.util.UUID

/**
 * Har som ansvar å hente skattegrunnlaget, og gjøre noe videre med denne
 */
class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val skattDokumentService: SkattDokumentService,
    val clock: Clock,
) : SkatteService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

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

    override fun hentLagOgJournalførSkattePdf(
        request: FrioppslagSkattRequest,
    ): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA> {
        return Skattegrunnlag(
            id = UUID.randomUUID(),
            fnr = request.fnr,
            hentetTidspunkt = Tidspunkt.now(clock),
            saksbehandler = request.saksbehandler,
            årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.fnr, request.år)
                .hentMestGyldigeSkattegrunnlagEllerFeil()
                .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvSkattemelding(it).left() },
            årSpurtFor = request.år.toRange(),
        ).let {
            log.info("Hentet skattegrunnlag for sakstype ${request.sakstype} med fagsystemId ${request.fagsystemId}")
            skattDokumentService.genererSkattePdfOgJournalfør(
                GenererSkattPdfOgJournalførRequest(
                    skattegrunnlag = it,
                    begrunnelse = request.begrunnelse,
                    fnr = request.fnr,
                    sakstype = request.sakstype,
                    fagsystemId = request.fagsystemId,
                ),
            )
        }
    }
}
