package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class SimuleringRequestBuilderTest {

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingsRequest = UtbetalingRequestTest.utbetalingRequestFørstegangsbehandling.oppdragRequest
        SimuleringRequestBuilder(utbetalingsRequest).build().request.also {
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
        val linjeSomSkalEndres = UtbetalingRequestTest.nyUtbetaling.sisteUtbetalingslinje()!!

        val linjeMedEndring = Utbetalingslinje.Endring(
            utbetalingslinje = linjeSomSkalEndres,
            statusendring = Utbetalingslinje.Statusendring(
                status = Utbetalingslinje.LinjeStatus.OPPHØR,
                fraOgMed = 1.februar(2020),
            ),
        )
        val utbetalingMedEndring = UtbetalingRequestTest.nyUtbetaling.copy(
            type = Utbetaling.UtbetalingsType.OPPHØR,
            avstemmingsnøkkel = Avstemmingsnøkkel(18.september(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(linjeMedEndring),
        )

        val utbetalingsRequest = toUtbetalingRequest(utbetalingMedEndring).oppdragRequest
        SimuleringRequestBuilder(utbetalingsRequest).build().request.let {
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
        brukKjoreplan shouldBe oppdragslinje.brukKjoreplan
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
