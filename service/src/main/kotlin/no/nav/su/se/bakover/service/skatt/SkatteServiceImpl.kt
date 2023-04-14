package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import java.time.Clock
import java.time.Year

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    val clock: Clock,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Skattegrunnlag = Skattegrunnlag(
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock).minusYears(1)).hentMestGyldigeSkattegrunnlag(),
        årSpurtFor = Year.now(clock).minusYears(1).toRange(),
    )

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        yearRange: YearRange,
    ): Skattegrunnlag = Skattegrunnlag(
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlagForÅrsperiode(fnr, yearRange).map { it.hentMestGyldigeSkattegrunnlag() }.toNonEmptyList(),
        årSpurtFor = yearRange,
    )
}
