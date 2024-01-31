package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.GenererSkattPdfRequest
import vilkår.skatt.application.KunneIkkeGenerereSkattePdfOgJournalføre
import vilkår.skatt.application.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import vilkår.skatt.application.SkatteService
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.skatt.domain.Skatteoppslag
import java.time.Clock
import java.time.Year
import java.util.UUID

/**
 * Har som ansvar å hente skattegrunnlaget, og gjøre noe videre med denne
 *
 * * TODO - på sikt vil vi at denne skal være i skattemodulen
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

    override fun hentOgLagSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA> {
        return hentSkattegrunnlag(request).getOrElse {
            return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.KunneIkkeHenteSkattemelding(it).left()
        }.let {
            skattDokumentService.genererSkattePdf(
                GenererSkattPdfRequest(
                    skattegrunnlagSøkers = it.first,
                    skattegrunnlagEps = it.second,
                    begrunnelse = request.begrunnelse,
                    sakstype = request.sakstype,
                    fagsystemId = request.fagsystemId,
                ),
            )
        }
    }

    override fun hentLagOgJournalførSkattePdf(
        request: FrioppslagSkattRequest,
    ): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA> {
        return hentSkattegrunnlag(request).getOrElse {
            return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvSkattemelding(it).left()
        }.let {
            log.info("Hentet skattegrunnlag for sakstype ${request.sakstype} med fagsystemId ${request.fagsystemId}")
            skattDokumentService.genererSkattePdfOgJournalfør(
                GenererSkattPdfRequest(
                    skattegrunnlagSøkers = it.first,
                    skattegrunnlagEps = it.second,
                    begrunnelse = request.begrunnelse,
                    sakstype = request.sakstype,
                    fagsystemId = request.fagsystemId,
                ),
            )
        }
    }

    private fun hentSkattegrunnlag(request: FrioppslagSkattRequest): Either<KunneIkkeHenteSkattemelding, Pair<Skattegrunnlag, Skattegrunnlag?>> {
        val skattegrunnlagSøkers = Skattegrunnlag(
            id = UUID.randomUUID(),
            fnr = request.fnr,
            hentetTidspunkt = Tidspunkt.now(clock),
            saksbehandler = request.saksbehandler,
            årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.fnr, request.år)
                .hentMestGyldigeSkattegrunnlagEllerFeil()
                .getOrElse { return it.tilKunneIkkeHenteSkattemelding().left() },
            årSpurtFor = request.år.toRange(),
        )

        val skattegrunnlagEps = if (request.epsFnr != null) {
            Skattegrunnlag(
                id = UUID.randomUUID(),
                fnr = request.epsFnr!!,
                hentetTidspunkt = Tidspunkt.now(clock),
                saksbehandler = request.saksbehandler,
                årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.epsFnr!!, request.år)
                    .hentMestGyldigeSkattegrunnlagEllerFeil()
                    .getOrElse { return it.tilKunneIkkeHenteSkattemelding().left() },
                årSpurtFor = request.år.toRange(),
            )
        } else {
            null
        }

        return Pair(skattegrunnlagSøkers, skattegrunnlagEps).right()
    }
}
