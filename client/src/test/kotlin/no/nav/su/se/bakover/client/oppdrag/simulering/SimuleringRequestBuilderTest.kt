package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock

internal class SimuleringRequestBuilderTest {

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingsRequest = UtbetalingRequestTest.utbetalingRequestFørstegangsutbetaling.oppdragRequest
        SimuleringRequestBuilder(
            simuleringsperiode = år(2020),
            mappedRequest = utbetalingsRequest,
        ).build().request.also {
            it.oppdrag.let { oppdrag ->
                oppdrag.assert(utbetalingsRequest)
                oppdrag.oppdragslinje.forEachIndexed { index, oppdragslinje ->
                    oppdragslinje.assert(utbetalingsRequest.oppdragslinjer[index])
                }
            }
            it.simuleringsPeriode.assert(
                fraOgMed = "2020-01-01",
                tilOgMed = "2020-12-31",
            )
        }
    }

    @Test
    fun `bygger simulering request ved endring av eksisterende oppdragslinjer`() {
        val linjeSomSkalEndres = UtbetalingRequestTest.nyUtbetaling.sisteUtbetalingslinje()

        val linjeMedEndring = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = linjeSomSkalEndres,
            virkningsperiode = Periode.create(1.februar(2020), linjeSomSkalEndres.periode.tilOgMed),
            clock = Clock.systemUTC(),
            rekkefølge = Rekkefølge.start(),
        )
        val utbetalingMedEndring = UtbetalingRequestTest.nyUtbetaling.copy(
            avstemmingsnøkkel = Avstemmingsnøkkel(18.september(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(linjeMedEndring),
        )

        val utbetalingsRequest = toUtbetalingRequest(utbetalingMedEndring).oppdragRequest
        SimuleringRequestBuilder(
            simuleringsperiode = Periode.create(
                fraOgMed = 1.februar(2020),
                tilOgMed = 31.desember(2020),
            ),
            mappedRequest = utbetalingsRequest,
        ).build().request.let {
            it.oppdrag.let { oppdrag ->
                oppdrag.assert(utbetalingsRequest)
                oppdrag.oppdragslinje.forEachIndexed { index, oppdragslinje ->
                    oppdragslinje.assert(utbetalingsRequest.oppdragslinjer[index])
                }
                oppdrag.oppdragslinje.forEachIndexed { index, oppdragslinje ->
                    oppdragslinje.assert(utbetalingsRequest.oppdragslinjer[index])
                }
            }
            it.simuleringsPeriode.assert(
                fraOgMed = "2020-02-01",
                tilOgMed = "2020-12-31",
            )
        }
    }

    @Test
    fun `opphører fra oktober og ut men simulerer hele siste utbetalingslinje`() {
        val linjeSomSkalEndres = UtbetalingRequestTest.nyUtbetaling.sisteUtbetalingslinje()

        val linjeMedEndring = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = linjeSomSkalEndres,
            virkningsperiode = Periode.create(1.oktober(2020), linjeSomSkalEndres.periode.tilOgMed),
            clock = Clock.systemUTC(),
            rekkefølge = Rekkefølge.start(),
        )
        val utbetalingMedEndring = UtbetalingRequestTest.nyUtbetaling.copy(
            avstemmingsnøkkel = Avstemmingsnøkkel(18.september(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(linjeMedEndring),
        )

        val utbetalingsRequest = toUtbetalingRequest(utbetalingMedEndring).oppdragRequest
        SimuleringRequestBuilder(
            simuleringsperiode = Periode.create(
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
            ),
            mappedRequest = utbetalingsRequest,
        ).build().request.let {
            it.oppdrag.let { oppdrag ->
                oppdrag.assert(utbetalingsRequest)
                oppdrag.oppdragslinje.forEachIndexed { index, oppdragslinje ->
                    oppdragslinje.assert(utbetalingsRequest.oppdragslinjer[index])
                }
                oppdrag.oppdragslinje.forEachIndexed { index, oppdragslinje ->
                    oppdragslinje.assert(utbetalingsRequest.oppdragslinjer[index])
                }
            }
            it.simuleringsPeriode.assert(
                fraOgMed = "2020-05-01",
                tilOgMed = "2020-12-31",
            )
        }
    }

    private fun SimulerBeregningRequest.SimuleringsPeriode.assert(fraOgMed: String, tilOgMed: String) {
        this.datoSimulerFom shouldBe fraOgMed
        this.datoSimulerTom shouldBe tilOgMed
    }

    private fun Oppdragslinje.assert(oppdragslinje: UtbetalingRequest.Oppdragslinje) {
        delytelseId shouldBe oppdragslinje.delytelseId
        kodeEndringLinje shouldBe oppdragslinje.kodeEndringLinje.value
        sats shouldBe BigDecimal(oppdragslinje.sats)
        typeSats shouldBe oppdragslinje.typeSats.value
        datoVedtakFom shouldBe oppdragslinje.datoVedtakFom
        datoVedtakTom shouldBe oppdragslinje.datoVedtakTom
        utbetalesTilId shouldBe oppdragslinje.utbetalesTilId
        refDelytelseId shouldBe oppdragslinje.refDelytelseId
        refFagsystemId shouldBe oppdragslinje.refFagsystemId
        kodeKlassifik shouldBe oppdragslinje.kodeKlassifik
        fradragTillegg.value() shouldBe oppdragslinje.fradragTillegg.value
        saksbehId shouldBe oppdragslinje.saksbehId
        brukKjoreplan shouldBe oppdragslinje.brukKjoreplan.value
        attestant[0].attestantId shouldBe oppdragslinje.saksbehId
        kodeStatusLinje?.value() shouldBe oppdragslinje.kodeStatusLinje?.value
        datoStatusFom shouldBe oppdragslinje.datoStatusFom
    }

    private fun Oppdrag.assert(utbetalingsRequest: UtbetalingRequest.OppdragRequest) {
        oppdragGjelderId shouldBe utbetalingsRequest.oppdragGjelderId
        saksbehId shouldBe utbetalingsRequest.saksbehId
        fagsystemId shouldBe utbetalingsRequest.fagsystemId
        kodeEndring shouldBe utbetalingsRequest.kodeEndring.value
        kodeFagomraade shouldBe utbetalingsRequest.kodeFagomraade
        utbetFrekvens shouldBe utbetalingsRequest.utbetFrekvens.value
        datoOppdragGjelderFom shouldBe utbetalingsRequest.datoOppdragGjelderFom
        enhet[0].datoEnhetFom shouldBe utbetalingsRequest.oppdragsEnheter[0].datoEnhetFom
        enhet[0].enhet shouldBe utbetalingsRequest.oppdragsEnheter[0].enhet
        enhet[0].typeEnhet shouldBe utbetalingsRequest.oppdragsEnheter[0].typeEnhet
    }
}
