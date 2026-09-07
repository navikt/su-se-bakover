package no.nav.su.se.bakover.service.historisk

import beregning.domain.BeregningFactory
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import beregning.domain.BeregningStrategy
import beregning.domain.Beregningsperiode
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock
import java.util.UUID

class BeregnHistoriskAlderServiceImpl(
    private val satsFactory: SatsFactory,
    private val utbetalingService: UtbetalingService,
    private val clock: Clock,
) {

    fun beregnHistoriskAlder(grunnlag: HistoriskAlderBeregning.Grunnlag): HistoriskAlderBeregning {
        val beregningsperioder = grunnlag.perioder.map {
            Beregningsperiode(
                periode = it.periode,
                strategy = it.utledBeregningsstrategi(satsFactory),
            )
        }
        val beregning = BeregningFactory(clock).ny(
            beregningsperioder = beregningsperioder,
            fradrag = grunnlag.fradrag,
        )

        val tidligereUtbetalinger = Utbetalinger()

        val utbetalingForSimulering = Utbetalingsstrategi.NyAldersUtbetaling(
            sakId = UUID.randomUUID(), // TODO finnes ikke i infotrygd så kan bare få et nytt et i suapp?
            saksnummer = Saksnummer(123L), // TODO erstatt med saksnummer for historisk
            fnr = Fnr.tryCreate("")!!,
            eksisterendeUtbetalinger = tidligereUtbetalinger,
            behandler = NavIdentBruker.Saksbehandler.systembruker(),
            beregning = beregning,
            clock = clock,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling,
            sakstype = Sakstype.ALDER,
            aksepterKvitteringMedFeil = false, // TODO hva betyr denne??
        ).generate()

        val simulertUtbetaling = simulerUtbetaling(
            tidligereUtbetalinger = tidligereUtbetalinger,
            utbetalingForSimulering = utbetalingForSimulering,
            simuler = utbetalingService::simulerUtbetaling,
        )

        return HistoriskAlderBeregning(beregning)
    }
}

data class HistoriskAlderBeregning(
    val beregning: BeregningMedFradragBeregnetMånedsvis,
) {
    data class Grunnlag(
        val perioder: List<HistoriskPeriodeMedStrategi>,
        val fradrag: List<Fradrag>,
    )
}

data class HistoriskPeriodeMedStrategi(
    val periode: Periode,
    val strategi: Strategi,
) {
    enum class Strategi {
        BorMedVoksne,
        EpsUnder67År,
        Eps67EllerEldre,
        EpsUnder67ÅrOgUførFlyktning,
        BorAlene,
    }
}

private fun HistoriskPeriodeMedStrategi.utledBeregningsstrategi(
    satsFactory: SatsFactory,
): BeregningStrategy {
    val sakstype = Sakstype.ALDER
    return when (this.strategi) {
        HistoriskPeriodeMedStrategi.Strategi.BorMedVoksne -> BeregningStrategy.BorMedVoksne(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.EpsUnder67År -> BeregningStrategy.EpsUnder67År(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.Eps67EllerEldre -> BeregningStrategy.Eps67EllerEldre(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.EpsUnder67ÅrOgUførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.BorAlene -> BeregningStrategy.BorAlene(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
    }
}
