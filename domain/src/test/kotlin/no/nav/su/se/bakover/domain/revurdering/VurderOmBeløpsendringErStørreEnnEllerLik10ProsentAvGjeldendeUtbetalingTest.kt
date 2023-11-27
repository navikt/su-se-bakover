package no.nav.su.se.bakover.domain.revurdering

import arrow.core.nonEmptyListOf
import behandling.domain.beregning.fradrag.FradragFactory
import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.revurdering.beregning.VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalinger
import no.nav.su.se.bakover.test.utbetaling.utbetalingerNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingerOpphør
import no.nav.su.se.bakover.test.utbetaling.utbetalingerReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingerStans
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeReaktivering
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeStans
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetalingTest {

    private val beregningsperiode = januar(2021)..april(2021)

    @Test
    fun `ingen utbetalinger overlapper med beregningsperioden gir true`() {
        val clock = TikkendeKlokke()
        val førsteId = UUID30.randomUUID()
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalinger(
                clock = clock,
                utbetalingslinjeNy(beløp = 5000, periode = desember(2020), id = førsteId, clock = clock),
                utbetalingslinjeNy(
                    beløp = 5000,
                    periode = desember(2021),
                    forrigeUtbetalingslinjeId = førsteId,
                    clock = clock,
                    rekkefølge = Rekkefølge.ANDRE,
                ),
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `alle måneder i ny beregning har endring større enn 10 prosent gir true`() {
        val clock = TikkendeKlokke()
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalinger(
                clock = clock,
                utbetalingslinjeNy(beløp = 5000, periode = beregningsperiode, clock = clock),
            ),
            nyBeregning = lagBeregningJanApr21(10000),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerNy(
                beløp = 5000,
                periode = beregningsperiode,
            ),
            nyBeregning = lagBeregningJanApr21(1000),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerReaktivering(
                beløp = 5000,
                nyPeriode = beregningsperiode,
            ),
            nyBeregning = lagBeregningJanApr21(10000),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerReaktivering(
                beløp = 5000,
                nyPeriode = beregningsperiode,
            ),
            nyBeregning = lagBeregningJanApr21(1000),
        ).resultat shouldBe true
    }

    @Test
    fun `10 prosent opp gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(5500),
        ).resultat shouldBe true
    }

    @Test
    fun `10 prosent ned gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(4500),
        ).resultat shouldBe true
    }

    @Test
    fun `alle måneder i ny beregning har endring lik 10 prosent gir true z`() {
        val clock = TikkendeKlokke()
        val førsteUtbetaling = utbetalingslinjeNy(
            beløp = 5000,
            clock = clock,
            rekkefølge = Rekkefølge.FØRSTE,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = førsteUtbetaling,
            clock = clock,
            virkningstidspunkt = førsteUtbetaling.originalFraOgMed(),
            rekkefølge = Rekkefølge.ANDRE,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = stans,
            clock = clock,
            virkningstidspunkt = førsteUtbetaling.originalFraOgMed(),
            rekkefølge = Rekkefølge.TREDJE,
        )
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(
                        førsteUtbetaling,
                        stans,
                        reaktivering,
                    ),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(4500),
        ).resultat shouldBe true
    }

    @Test
    fun `ingen måneder i ny beregning har endring større enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(5250),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(4750),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerReaktivering(
                beløp = 5000,
                nyPeriode = beregningsperiode,
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder bortsett fra første har endring større enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregning(
                januar(2021) to 5000,
                februar(2021) to 10000,
                mars(2021) to 1000,
                april(2021) to 20000,
            ),
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder bortsett fra første har ikke endring større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(5000)),
                ),
            ),
            nyBeregning = lagBeregning(
                januar(2021) to 15000,
                februar(2021) to 5000,
                mars(2021) to 5000,
                april(2021) to 5000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer på opphørsdato er mindre enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerOpphør(
                nyPeriode = beregningsperiode,
                opphørsperiode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer på opphørsdato er større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerOpphør(
                nyPeriode = beregningsperiode,
                opphørsperiode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(15000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer senere enn opphørsdato er mindre enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerOpphør(
                nyPeriode = beregningsperiode,
                opphørsperiode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer senere enn opphørsdato større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerOpphør(
                nyPeriode = beregningsperiode,
                opphørsperiode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(15000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er stans og endringer senere enn dato for stans er mindre enn 10 prosent gir true`() {
        val clock = TikkendeKlokke()
        val førsteUtbetaling = utbetalingslinjeNy(
            beløp = 5000,
            clock = clock,
            rekkefølge = Rekkefølge.FØRSTE,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = førsteUtbetaling,
            virkningstidspunkt = førsteUtbetaling.originalFraOgMed(),
            clock = clock,
            rekkefølge = Rekkefølge.ANDRE,
        )
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(
                        førsteUtbetaling,
                        stans,
                    ),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er stans og endringer senere enn dato for stans større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerStans(
                nyPeriode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(15000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er reaktivert og endringer senere enn reaktiveringsdato er mindre enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerReaktivering(
                nyPeriode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(5000),
        ).resultat shouldBe false
    }

    @Test
    fun `gjeldende utbetaling er reaktivert og endringer senere enn reaktiveringsdato større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = utbetalingerReaktivering(
                nyPeriode = beregningsperiode,
                beløp = 5000,
            ),
            nyBeregning = lagBeregningJanApr21(15000),
        ).resultat shouldBe true
    }

    @Test
    fun `ny beregning er under minstegrense for utbetaling, men differanse mot gjeldende utebetaling er mindre enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(440)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(405),
        ).resultat shouldBe false
    }

    @Test
    fun `ny beregning er under minstegrense for utbetaling, og differanse mot gjeldende utebetaling er større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(lagUtbetaling(440)),
                ),
            ),
            nyBeregning = lagBeregningJanApr21(390),
        ).resultat shouldBe true
    }

    @Test
    fun `blandet drops gir forskjellig resultat avhengig av beregningens første måned`() {
        val clock = TikkendeKlokke()
        val første = utbetalingslinjeNy(
            beløp = 5000,
            periode = år(2021),
            clock = clock,
        )
        val stans = utbetalingslinjeStans(
            utbetalingslinjeSomSkalEndres = første,
            virkningstidspunkt = 1.februar(2021),
            clock = clock,
        )
        val reaktivering = utbetalingslinjeReaktivering(
            virkningstidspunkt = 1.mars(2021),
            utbetalingslinjeSomSkalEndres = stans,
            clock = clock,
        )
        val andre = utbetalingslinjeNy(
            forrigeUtbetalingslinjeId = reaktivering.id,
            beløp = 10000,
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            clock = clock,
            rekkefølge = Rekkefølge.FJERDE,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = andre,
            virkningsperiode = Periode.create(
                fraOgMed = 1.desember(2021),
                tilOgMed = andre.periode.tilOgMed,
            ),
            clock = clock,
            rekkefølge = Rekkefølge.FEMTE,
        )

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                år(2021) to 5000,
            ),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                år(2021) to 6000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.februar(2021), 31.desember(2021)) to 5000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.februar(2021), 31.desember(2021)) to 500,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.mars(2021), 31.desember(2021)) to 5000,
            ),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.mars(2021), 31.desember(2021)) to 6000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.mai(2021), 31.desember(2021)) to 5000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                Periode.create(1.mai(2021), 31.desember(2021)) to 10000,
            ),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                desember(2021) to 10000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = Utbetalinger(
                oversendtUtbetalingUtenKvittering(
                    utbetalingslinjer = nonEmptyListOf(første, stans, reaktivering, andre, opphør),
                ),
            ),
            nyBeregning = lagBeregning(
                desember(2021) to 5000,
            ),
        ).resultat shouldBe true
    }

    private fun lagUtbetaling(
        månedsbeløp: Int,
        periode: Periode = beregningsperiode,
        utbetalingsIndex: Int = 0,
        id: UUID30 = UUID30.randomUUID(),
        forrigeUtbetalingslinjeId: UUID30? = null,
    ): Utbetalingslinje.Ny {
        return utbetalingslinjeNy(
            id = id,
            opprettet = fixedTidspunkt.plus(utbetalingsIndex.toLong(), ChronoUnit.SECONDS),
            periode = periode,
            beløp = månedsbeløp,
            rekkefølge = if (utbetalingsIndex == 0) Rekkefølge.start() else Rekkefølge.skip(utbetalingsIndex.toLong() - 1),
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        )
    }

    private fun lagStans(
        stansFraOgMedDato: LocalDate = beregningsperiode.fraOgMed,
        forrigeUtbetaling: Utbetalingslinje,
        utbetalingsIndex: Int = 0,
    ) =
        Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = forrigeUtbetaling,
            virkningstidspunkt = stansFraOgMedDato,
            clock = fixedClock.plus(utbetalingsIndex.toLong(), ChronoUnit.SECONDS),
            rekkefølge = if (utbetalingsIndex == 0) Rekkefølge.start() else Rekkefølge.skip(utbetalingsIndex.toLong() - 1),
        )

    private fun lagReaktivert(
        reaktiverDato: LocalDate = beregningsperiode.fraOgMed,
        forrigeUtbetaling: Utbetalingslinje,
        utbetalingsIndex: Int = 0,
    ) =
        Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = forrigeUtbetaling,
            virkningstidspunkt = reaktiverDato,
            clock = fixedClock.plus(utbetalingsIndex.toLong(), ChronoUnit.SECONDS),
            if (utbetalingsIndex == 0) Rekkefølge.start() else Rekkefølge.skip(utbetalingsIndex.toLong() - 1),
        )

    private fun lagBeregningJanApr21(månedsbeløp: Int): Beregning {
        return lagBeregning(beregningsperiode to månedsbeløp)
    }

    private fun lagBeregning(vararg periodeBeløpMap: Pair<Periode, Int>): Beregning {
        val fradrag = periodeBeløpMap.map {
            val sats = satsFactoryTestPåDato().høyUføre(it.first.måneder().head).satsForMånedAsDouble
            val diff = abs(sats - it.second)
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = diff,
                periode = it.first,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
        val periode = periodeBeløpMap.map { it.first }
            .let { perioder -> Periode.create(perioder.minOf { it.fraOgMed }, perioder.maxOf { it.tilOgMed }) }
        return BeregningFactory(clock = fixedClock).ny(
            fradrag = fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
    }
}
