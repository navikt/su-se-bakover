package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class SimuleringRequestBuilderTest {

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingsRequest = UtbetalingRequestTest.utbetalingRequestFørstegangsbehandling.oppdragRequest
        SimuleringRequestBuilder(utbetalingsRequest).build().request.oppdrag.also {
            it.oppdragGjelderId shouldBe utbetalingsRequest.oppdragGjelderId
            it.saksbehId shouldBe utbetalingsRequest.saksbehId
            it.fagsystemId shouldBe utbetalingsRequest.fagsystemId
            it.kodeEndring shouldBe utbetalingsRequest.kodeEndring.value
            it.kodeFagomraade shouldBe utbetalingsRequest.kodeFagomraade
            it.utbetFrekvens shouldBe utbetalingsRequest.utbetFrekvens.value
            it.datoOppdragGjelderFom shouldBe utbetalingsRequest.datoOppdragGjelderFom
            it.enhet[0].datoEnhetFom shouldBe utbetalingsRequest.oppdragsEnheter[0].datoEnhetFom
            it.enhet[0].enhet shouldBe utbetalingsRequest.oppdragsEnheter[0].enhet
            it.enhet[0].typeEnhet shouldBe utbetalingsRequest.oppdragsEnheter[0].typeEnhet
            it.oppdragslinje[0].also { oppdragslinje ->
                oppdragslinje.delytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].delytelseId
                oppdragslinje.kodeEndringLinje shouldBe utbetalingsRequest.oppdragslinjer[0].kodeEndringLinje.value
                oppdragslinje.sats shouldBe BigDecimal(utbetalingsRequest.oppdragslinjer[0].sats)
                oppdragslinje.typeSats shouldBe utbetalingsRequest.oppdragslinjer[0].typeSats.value
                oppdragslinje.datoVedtakFom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakFom
                oppdragslinje.datoVedtakTom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakTom
                oppdragslinje.utbetalesTilId shouldBe utbetalingsRequest.oppdragslinjer[0].utbetalesTilId
                oppdragslinje.refDelytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].refDelytelseId
                oppdragslinje.refFagsystemId shouldBe utbetalingsRequest.oppdragslinjer[0].refFagsystemId
                oppdragslinje.kodeKlassifik shouldBe utbetalingsRequest.oppdragslinjer[0].kodeKlassifik
                oppdragslinje.fradragTillegg shouldBe utbetalingsRequest.oppdragslinjer[0].fradragTillegg.value.let { FradragTillegg.valueOf(it) }
                oppdragslinje.saksbehId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                oppdragslinje.brukKjoreplan shouldBe utbetalingsRequest.oppdragslinjer[0].brukKjoreplan
                oppdragslinje.attestant[0].attestantId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
            }
        }
    }
}
