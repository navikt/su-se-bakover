package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg.T
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class SimuleringRequestBuilderTest {
    private companion object {
        private const val FAGOMRÅDE = "SUUFORE"
        private const val ENDRINGSKODE_NY = "NY"
        private const val PERSON = "12345678911"
        private const val BELØP = 1000
        private const val SAKSBEHANDLER = "SU"
        private const val KLASSEKODE = "SUUFORE"
        private val yyyyMMdd = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val oppdragId = UUID30.randomUUID()
    }

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val oppdragslinjeid1 = UUID30.randomUUID()
        val oppdragslinjeid2 = UUID30.randomUUID()
        val simuleringRequest = createOppdrag(
            listOf(
                Utbetalingslinje(
                    id = oppdragslinjeid1,
                    fom = 1.januar(2020),
                    tom = 14.januar(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = null,
                ),
                Utbetalingslinje(
                    id = oppdragslinjeid2,
                    fom = 15.januar(2020),
                    tom = 31.januar(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = oppdragslinjeid1,
                )
            )
        )
        simuleringRequest.request.simuleringsPeriode.datoSimulerFom shouldBe 1.januar(2020).format(yyyyMMdd)
        simuleringRequest.request.simuleringsPeriode.datoSimulerTom shouldBe 31.januar(2020).format(yyyyMMdd)

        assertOppdrag(simuleringRequest.request.oppdrag, ENDRINGSKODE_NY)
        assertOppdragslinje(
            oppdrag = simuleringRequest.request.oppdrag,
            index = 0,
            delytelseId = oppdragslinjeid1.toString(),
            endringskode = ENDRINGSKODE_NY,
            fom = 1.januar(2020),
            tom = 14.januar(2020),
            datoStatusFom = null,
            statuskode = null,
            refDelytelsesId = null,
            refFagsystemId = null
        )
        assertOppdragslinje(
            oppdrag = simuleringRequest.request.oppdrag,
            index = 1,
            delytelseId = oppdragslinjeid2.toString(),
            endringskode = ENDRINGSKODE_NY,
            fom = 15.januar(2020),
            tom = 31.januar(2020),
            datoStatusFom = null,
            statuskode = null,
            refDelytelsesId = oppdragslinjeid1.toString(),
            refFagsystemId = oppdragId.toString()
        )
    }

    @Test
    fun `bygger simulering request til bruker med eksisterende oppdragslinjer`() {
        val oppdragslinjeid1 = UUID30.randomUUID()
        val oppdragslinjeid2 = UUID30.randomUUID()
        val eksisterendeOppdragslinjeid = UUID30.randomUUID()
        val simuleringRequest = createOppdrag(
            listOf(
                Utbetalingslinje(
                    id = oppdragslinjeid1,
                    fom = 1.januar(2020),
                    tom = 14.januar(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeid,
                ),
                Utbetalingslinje(
                    id = oppdragslinjeid2,
                    fom = 15.januar(2020),
                    tom = 31.januar(2020),
                    beløp = BELØP,
                    forrigeUtbetalingslinjeId = oppdragslinjeid1,
                )
            )
        )
        simuleringRequest.request.simuleringsPeriode.datoSimulerFom shouldBe 1.januar(2020).format(yyyyMMdd)
        simuleringRequest.request.simuleringsPeriode.datoSimulerTom shouldBe 31.januar(2020).format(yyyyMMdd)

        assertOppdrag(simuleringRequest.request.oppdrag, ENDRINGSKODE_NY)
        assertOppdragslinje(
            oppdrag = simuleringRequest.request.oppdrag,
            index = 0,
            delytelseId = oppdragslinjeid1.toString(),
            endringskode = ENDRINGSKODE_NY,
            fom = 1.januar(2020),
            tom = 14.januar(2020),
            datoStatusFom = null,
            statuskode = null,
            refDelytelsesId = eksisterendeOppdragslinjeid.toString(),
            refFagsystemId = oppdragId.toString()
        )
        assertOppdragslinje(
            oppdrag = simuleringRequest.request.oppdrag,
            index = 1,
            delytelseId = oppdragslinjeid2.toString(),
            endringskode = ENDRINGSKODE_NY,
            fom = 15.januar(2020),
            tom = 31.januar(2020),
            datoStatusFom = null,
            statuskode = null,
            refDelytelsesId = oppdragslinjeid1.toString(),
            refFagsystemId = oppdragId.toString()
        )
    }

    private fun createOppdrag(
        oppdragslinjer: List<Utbetalingslinje>
    ): SimulerBeregningRequest {

        val builder = SimuleringRequestBuilder(
            Utbetaling(
                behandlingId = UUID.randomUUID(),
                utbetalingslinjer = oppdragslinjer,
                oppdragId = oppdragId
            ),
            PERSON
        )
        return builder.build()
    }

    private fun assertOppdrag(
        oppdrag: no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag,
        endringskode: String
    ) {
        oppdrag.oppdragGjelderId shouldBe PERSON
        oppdrag.saksbehId shouldBe SAKSBEHANDLER
        oppdrag.fagsystemId shouldBe oppdragId.toString()
        oppdrag.kodeEndring shouldBe endringskode
        oppdrag.kodeFagomraade shouldBe FAGOMRÅDE
        oppdrag.utbetFrekvens shouldBe "MND"
        oppdrag.datoOppdragGjelderFom shouldBe LocalDate.EPOCH.format(yyyyMMdd)
        oppdrag.enhet[0].datoEnhetFom shouldBe LocalDate.EPOCH.format(yyyyMMdd)
        oppdrag.enhet[0].enhet shouldBe "8020"
        oppdrag.enhet[0].typeEnhet shouldBe "BOS"
    }

    private fun assertOppdragslinje(
        oppdrag: Oppdrag,
        index: Int,
        delytelseId: String,
        endringskode: String,
        fom: LocalDate,
        tom: LocalDate,
        datoStatusFom: LocalDate?,
        statuskode: KodeStatusLinje?,
        refDelytelsesId: String?,
        refFagsystemId: String?
    ) {
        oppdrag.oppdragslinje[index].delytelseId shouldBe delytelseId
        oppdrag.oppdragslinje[index].kodeEndringLinje shouldBe endringskode
        oppdrag.oppdragslinje[index].sats shouldBe BELØP.toBigDecimal()
        oppdrag.oppdragslinje[index].typeSats shouldBe "MND"
        oppdrag.oppdragslinje[index].datoVedtakFom shouldBe fom.format(yyyyMMdd)
        oppdrag.oppdragslinje[index].datoVedtakTom shouldBe tom.format(yyyyMMdd)
        oppdrag.oppdragslinje[index].datoStatusFom shouldBe datoStatusFom?.format(yyyyMMdd)
        oppdrag.oppdragslinje[index].utbetalesTilId shouldBe PERSON
        oppdrag.oppdragslinje[index].refDelytelseId shouldBe refDelytelsesId
        oppdrag.oppdragslinje[index].refFagsystemId shouldBe refFagsystemId
        oppdrag.oppdragslinje[index].kodeStatusLinje shouldBe statuskode
        oppdrag.oppdragslinje[index].kodeKlassifik shouldBe KLASSEKODE
        oppdrag.oppdragslinje[index].fradragTillegg shouldBe T
        oppdrag.oppdragslinje[index].saksbehId shouldBe SAKSBEHANDLER
        oppdrag.oppdragslinje[index].brukKjoreplan shouldBe "N"
        oppdrag.oppdragslinje[index].attestant[0].attestantId shouldBe SAKSBEHANDLER
    }
}
