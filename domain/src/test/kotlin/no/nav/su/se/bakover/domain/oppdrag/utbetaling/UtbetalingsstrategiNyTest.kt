package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingsstrategiNyTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)

    private val fnr = Fnr("12345678910")

    private object BeregningMedTomMånedsbereninger : Beregning {
        override fun getId(): UUID = mock()
        override fun getOpprettet(): Tidspunkt = mock()
        override fun getMånedsberegninger(): List<Månedsberegning> = emptyList()
        override fun getFradrag(): List<Fradrag> = emptyList()
        override fun getSumYtelse(): Int = 1000
        override fun getSumFradrag(): Double = 1000.0
        override fun getBegrunnelse(): String = mock()
        override fun equals(other: Any?): Boolean = mock()
        override val periode: Periode = Periode.create(1.juni(2021), 30.november(2021))
    }

    @Test
    fun `ingen eksisterende utbetalinger`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(1.januar(2020), 30.april(2020)),
            utbetalinger = listOf(),
            clock = fixedClock,
            uføregrunnlag = uføregrunnlagListe,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        val first = actual.utbetalingslinjer.first()
        actual shouldBe expectedUtbetaling(
            actual,
            nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = first.id,
                    opprettet = first.opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )
    }

    @Test
    fun `nye utbetalingslinjer skal refere til forutgående utbetalingslinjer`() {
        val forrigeUtbetalingslinjeId = UUID30.randomUUID()

        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2020),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        val eksisterendeUtbetalinger = listOf(
            Utbetaling.UtbetalingForSimulering(
                opprettet = fixedTidspunkt,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = forrigeUtbetalingslinjeId,
                        opprettet = Tidspunkt.MIN,
                        fraOgMed = 1.januar(2018),
                        tilOgMed = 31.desember(2018),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 5000,
                        uføregrad = Uføregrad.parse(50),
                    ),
                ),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
                sakstype = Sakstype.UFØRE,
            ).toSimulertUtbetaling(
                simulering = Simulering(
                    gjelderId = fnr,
                    gjelderNavn = "navn",
                    datoBeregnet = idag(fixedClock),
                    nettoBeløp = 0,
                    periodeList = listOf(),
                ),

            ).toOversendtUtbetaling(
                oppdragsmelding = Utbetalingsrequest(
                    value = "",
                ),
            ).toKvittertUtbetaling(
                kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "", mottattTidspunkt = fixedTidspunkt),
            ),
        )

        val nyUtbetaling = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = eksisterendeUtbetalinger,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
            ),
            clock = fixedClock,
            uføregrunnlag = uføregrunnlagListe,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        nyUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val dummyUtbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
                uføregrad = Uføregrad.parse(50),
            ),
        )

        val first = Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = 1.januar(2020).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = dummyUtbetalingslinjer,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),

        ).toKvittertUtbetaling(
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "", mottattTidspunkt = fixedTidspunkt),
        )

        val second = Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = 1.februar(2020).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = dummyUtbetalingslinjer,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.februar(2020).startOfDay()),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),

        ).toKvittertUtbetaling(
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, "", mottattTidspunkt = fixedTidspunkt),
        )

        val third = Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = 1.mars(2020).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = dummyUtbetalingslinjer,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.mars(2020).startOfDay()),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),
        ).toKvittertUtbetaling(
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, "", mottattTidspunkt = fixedTidspunkt),
        )
        val fourth = Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = 1.juli(2020).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = dummyUtbetalingslinjer,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(1.juli(2020).startOfDay()),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),

        ).toKvittertUtbetaling(
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, "", mottattTidspunkt = fixedTidspunkt),

        )
        val utbetalinger = listOf(first, second, third, fourth)
        utbetalinger.hentOversendteUtbetalingerUtenFeil()[1] shouldBe third
    }

    @Test
    fun `konverterer tilstøtende beregningsperioder med forskjellig beløp til separate utbetalingsperioder`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2020),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actualUtbetaling = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            clock = fixedClock,
            uføregrunnlag = uføregrunnlagListe,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 20946,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )
    }

    @Test
    fun `perioder som har likt beløp, men ikke tilstøter hverandre får separate utbetalingsperioder`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actualUtbetaling = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 4000.0,
                        periode = februar(2020),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = Periode.create(1.januar(2020), 30.april(2020)),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTest),
                    ),
                ),
            ),
            clock = fixedClock,
            uføregrunnlag = uføregrunnlagListe,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 19637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.februar(2020),
                    tilOgMed = 29.februar(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 16637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[2].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[2].opprettet,
                    fraOgMed = 1.mars(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[1].id,
                    beløp = actualUtbetaling.utbetalingslinjer[0].beløp,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )
    }

    @Test
    fun `kaster exception hvis månedsberegning og uføregrunnlag er tomme (0 til 0)`() {
        val uføreList = listOf<Grunnlag.Uføregrunnlag>()

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes uføregrunnlag (0 til 1)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes flere uføregrunnlag (0 til mange)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis det finnes månedsberegning, men uføregrunnlag er tom (1 til 0)`() {
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = createBeregning(1.januar(2021), 31.desember(2021)),
                uføregrunnlag = emptyList(),
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis det finnes flere månedsberegninger, men uføregrunnlag er tom (mange til 0)`() {
        val periode = år(2021)
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningFactory(clock = fixedClock).ny(
                    fradrag = listOf(
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            periode = periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Sosialstønad,
                            månedsbeløp = 1000.0,
                            periode = Periode.create(1.januar(2021), 31.mai(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Kontantstøtte,
                            månedsbeløp = 3000.0,
                            periode = Periode.create(1.juni(2021), 31.desember(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    beregningsperioder = listOf(
                        Beregningsperiode(
                            periode = periode,
                            strategy = BeregningStrategy.BorAlene(satsFactoryTest),
                        ),
                    ),
                ),
                uføregrunnlag = emptyList(),
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }
    }

    @Test
    fun `legger på uføregrad på utbetalingslinjer (1 til 1)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.mai(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere uføregrader til riktig utbetalingslinje for periode (1 til mange)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.mai(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 2
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere beregningsperioder til ufregrunnlag (mange til 1)`() {
        val periode = år(2021)
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Sosialstønad,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Kontantstøtte,
                        månedsbeløp = 8000.0,
                        periode = Periode.create(1.juni(2021), 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = BeregningStrategy.BorAlene(satsFactoryTest),
                    ),
                ),
            ),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 2
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 14946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 11946,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere beregningsperioder til flere ufregrunnlag (mange til mange)`() {
        val periode = år(2021)
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Sosialstønad,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Kontantstøtte,
                        månedsbeløp = 8000.0,
                        periode = Periode.create(1.juni(2021), 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = BeregningStrategy.BorAlene(satsFactoryTest),
                    ),
                ),
            ),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 2
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 14946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 11946,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `kaster exception hvis uføregrunnalget ikke inneholder alle beregningsperiodene`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = createBeregning(1.januar(2021), 31.desember(2021)),
                uføregrunnlag = uføreList,
                kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                sakstype = Sakstype.UFØRE,
            ).generate()
        }.also {
            it.message shouldContain "Uføregrunnlaget inneholder ikke alle beregningsperiodene. Grunnlagsperiodene:"
        }
    }

    @Test
    fun `må eksistere uføreperiode for alle månedsberegninger`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.juni(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `utbetalingslinje med uføregrad følger beregningsperiode`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.NyUføreUtbetaling(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.juni(2021), 30.november(2021)),
            uføregrunnlag = uføreList,
            kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            sakstype = Sakstype.UFØRE,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 30.november(2021),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    private fun expectedUtbetaling(
        actual: Utbetaling.UtbetalingForSimulering,
        oppdragslinjer: NonEmptyList<Utbetalingslinje>,
    ): Utbetaling.UtbetalingForSimulering {
        return Utbetaling.UtbetalingForSimulering(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = oppdragslinjer,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )
    }

    private fun expectedUtbetalingslinje(
        utbetalingslinjeId: UUID30,
        opprettet: Tidspunkt,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        beløp: Int,
        forrigeUtbetalingslinjeId: UUID30?,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ): Utbetalingslinje {
        return Utbetalingslinje.Ny(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp,
            uføregrad = uføregrad,
        )
    }

    private fun createBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate) = BeregningFactory(clock = fixedClock).ny(
        fradrag = listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = Periode.create(fraOgMed, tilOgMed),
                strategy = BeregningStrategy.BorAlene(satsFactoryTest),
            ),
        ),
    )
}
