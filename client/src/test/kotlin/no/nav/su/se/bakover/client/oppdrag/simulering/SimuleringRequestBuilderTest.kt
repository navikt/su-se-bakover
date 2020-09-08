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
            it.oppdragslinje[0].also {
                it.delytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].delytelseId
                it.kodeEndringLinje shouldBe utbetalingsRequest.oppdragslinjer[0].kodeEndringLinje.value
                it.sats shouldBe utbetalingsRequest.oppdragslinjer[0].sats.let { BigDecimal(it) }
                it.typeSats shouldBe utbetalingsRequest.oppdragslinjer[0].typeSats.value
                it.datoVedtakFom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakFom
                it.datoVedtakTom shouldBe utbetalingsRequest.oppdragslinjer[0].datoVedtakTom
                it.utbetalesTilId shouldBe utbetalingsRequest.oppdragslinjer[0].utbetalesTilId
                it.refDelytelseId shouldBe utbetalingsRequest.oppdragslinjer[0].refDelytelseId
                it.refFagsystemId shouldBe utbetalingsRequest.oppdragslinjer[0].refFagsystemId
                it.kodeKlassifik shouldBe utbetalingsRequest.oppdragslinjer[0].kodeKlassifik
                it.fradragTillegg shouldBe utbetalingsRequest.oppdragslinjer[0].fradragTillegg.value.let { FradragTillegg.valueOf(it) }
                it.saksbehId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
                it.brukKjoreplan shouldBe utbetalingsRequest.oppdragslinjer[0].brukKjoreplan
                it.attestant[0].attestantId shouldBe utbetalingsRequest.oppdragslinjer[0].saksbehId
            }
        }
    }
}
