package no.nav.su.se.bakover.client.stubs.oppdrag

import no.nav.su.se.bakover.client.oppdrag.Utbetalingslinjer
import no.nav.su.se.bakover.client.oppdrag.simulering.Detaljer
import no.nav.su.se.bakover.client.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResult
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringStatus
import no.nav.su.se.bakover.client.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.client.oppdrag.simulering.Utbetaling
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar

object SimuleringStub : no.nav.su.se.bakover.client.oppdrag.Simulering {
    override fun simulerOppdrag(utbetalingslinjer: Utbetalingslinjer) =
        SimuleringResult(
            status = SimuleringStatus.OK,
            feilmelding = null,
            simulering = Simulering(
                gjelderId = "gjelderId",
                gjelderNavn = "gjelderNavn",
                datoBeregnet = idag(),
                totalBelop = 15000,
                periodeList = listOf(
                    SimulertPeriode(
                        fom = 1.januar(2020),
                        tom = 31.desember(2020),
                        utbetaling = listOf(
                            Utbetaling(
                                fagSystemId = "SUP",
                                feilkonto = false,
                                forfall = 2.februar(2020),
                                utbetalesTilId = "utbetalesTilId",
                                utbetalesTilNavn = "utbetalestTilNavn",
                                detaljer = listOf(
                                    Detaljer(
                                        faktiskFom = 1.januar(2020),
                                        faktiskTom = 31.desember(2020),
                                        klassekode = "klassekode",
                                        sats = 240,
                                        antallSats = 20,
                                        belop = 15000,
                                        klassekodeBeskrivelse = "klassekodebeskrivelse",
                                        konto = "1234.12.12345",
                                        refunderesOrgNr = "refundersOrgnr",
                                        tilbakeforing = false,
                                        typeSats = "MND",
                                        uforegrad = 50,
                                        utbetalingsType = "utbetalingstype"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
}
