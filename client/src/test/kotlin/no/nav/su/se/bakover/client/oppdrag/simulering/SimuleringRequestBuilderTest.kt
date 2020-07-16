package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.Utbetalingslinjer
import no.nav.su.se.bakover.common.januar
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class SimuleringRequestBuilderTest {

    private companion object {
        private const val FAGOMRÅDE = "SU_UFØR"
        private const val ENDRINGSKODE_NY = "NY"
        private const val ENDRINGSKODE_ENDRET = "ENDR"
        private const val ENDRINGSKODE_UENDRET = "UEND"
        private const val PERSON = "12345678911"
        private const val ORGNR = "123456789"
        private const val FAGSYSTEMID = "a1b0c2"
        private const val DAGSATS = 1000
        private const val GRAD = 100
        private const val SAKSBEHANDLER = "Spenn"
        private val MAKSDATO = LocalDate.MAX
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    @Test
    fun `bygger simulering request til bruker`() {
        val simuleringRequest = simuleringRequestBruker(ENDRINGSKODE_ENDRET) {
            linje(
                Utbetalingslinjer.Utbetalingslinje(
                    delytelseId = "1",
                    endringskode = ENDRINGSKODE_NY,
                    klassekode = "SP",
                    fom = 1.januar(2020),
                    tom = 14.januar(2020),
                    dagsats = DAGSATS,
                    grad = GRAD,
                    refDelytelseId = null,
                    refFagsystemId = null,
                    datoStatusFom = null,
                    statuskode = null
                )
            )
            linje(
                Utbetalingslinjer.Utbetalingslinje(
                    delytelseId = "2",
                    endringskode = ENDRINGSKODE_NY,
                    klassekode = "SP",
                    fom = 15.januar(2020),
                    tom = 31.januar(2020),
                    dagsats = DAGSATS,
                    grad = GRAD,
                    refDelytelseId = null,
                    refFagsystemId = null,
                    datoStatusFom = null,
                    statuskode = null
                )
            )
        }
        assertEquals(1.januar(2020).format(tidsstempel), simuleringRequest.request.simuleringsPeriode.datoSimulerFom)
        assertEquals(31.januar(2020).format(tidsstempel), simuleringRequest.request.simuleringsPeriode.datoSimulerTom)
        assertOppdrag(simuleringRequest.request.oppdrag, ENDRINGSKODE_ENDRET)
        assertBrukerlinje(simuleringRequest.request.oppdrag, 0, "1", ENDRINGSKODE_NY, 1.januar(2020), 14.januar(2020))
        assertBrukerlinje(simuleringRequest.request.oppdrag, 1, "2", ENDRINGSKODE_NY, 15.januar(2020), 31.januar(2020))
    }

    @Test
    fun `brukerlinje opphører`() {
        val simuleringRequest = simuleringRequestBruker(ENDRINGSKODE_ENDRET) {
            linje(
                Utbetalingslinjer.Utbetalingslinje(
                    delytelseId = "1",
                    endringskode = "ENDR",
                    klassekode = "SP",
                    fom = 1.januar(2020),
                    tom = 31.januar(2020),
                    dagsats = DAGSATS,
                    grad = GRAD,
                    refDelytelseId = null,
                    refFagsystemId = null,
                    datoStatusFom = 1.januar(2020),
                    statuskode = "OPPH"
                )
            )
        }
        assertOppdrag(simuleringRequest.request.oppdrag, ENDRINGSKODE_ENDRET)
        assertBrukerlinje(
            oppdrag = simuleringRequest.request.oppdrag,
            index = 0,
            delytelseId = "1",
            endringskode = "ENDR",
            fom = 1.januar(2020),
            tom = 31.januar(2020),
            datoStatusFom = 1.januar(2020),
            statuskode = KodeStatusLinje.OPPH
        )
    }

    private fun simuleringRequestBruker(
        endringskode: String,
        block: Utbetalingslinjer.() -> Unit
    ): SimulerBeregningRequest {
        val builder = SimuleringRequestBuilder(
            Utbetalingslinjer(
                fagområde = FAGOMRÅDE,
                fagsystemId = FAGSYSTEMID,
                fødselsnummer = PERSON,
                endringskode = endringskode,
                saksbehandler = SAKSBEHANDLER
            ).apply(block)
        )
        return builder.build()
    }

    private fun assertOppdrag(oppdrag: Oppdrag, endringskode: String) {
        assertEquals(PERSON, oppdrag.oppdragGjelderId)
        assertEquals(SAKSBEHANDLER, oppdrag.saksbehId)
        assertEquals(FAGSYSTEMID, oppdrag.fagsystemId)
        assertEquals(endringskode, oppdrag.kodeEndring)
    }

    private fun assertBrukerlinje(
        oppdrag: Oppdrag,
        index: Int,
        delytelseId: String,
        endringskode: String,
        fom: LocalDate,
        tom: LocalDate,
        datoStatusFom: LocalDate? = null,
        statuskode: KodeStatusLinje? = null
    ) {
        assertOppdragslinje(oppdrag, index, delytelseId, endringskode, fom, tom, datoStatusFom, statuskode)
        assertEquals(PERSON, oppdrag.oppdragslinje[index].utbetalesTilId)
    }

    private fun assertOppdragslinje(
        oppdrag: Oppdrag,
        index: Int,
        delytelseId: String,
        endringskode: String,
        fom: LocalDate,
        tom: LocalDate,
        datoStatusFom: LocalDate?,
        statuskode: KodeStatusLinje?
    ) {
        assertEquals(delytelseId, oppdrag.oppdragslinje[index].delytelseId)
        assertEquals(endringskode, oppdrag.oppdragslinje[index].kodeEndringLinje)
        assertEquals(DAGSATS.toBigDecimal(), oppdrag.oppdragslinje[index].sats)
        assertEquals(GRAD.toBigInteger(), oppdrag.oppdragslinje[index].grad.first().grad)
        assertEquals(fom.format(tidsstempel), oppdrag.oppdragslinje[index].datoVedtakFom)
        assertEquals(tom.format(tidsstempel), oppdrag.oppdragslinje[index].datoVedtakTom)
        assertEquals(datoStatusFom?.format(tidsstempel), oppdrag.oppdragslinje[index].datoStatusFom)
        assertEquals(statuskode, oppdrag.oppdragslinje[index].kodeStatusLinje)
    }
}
