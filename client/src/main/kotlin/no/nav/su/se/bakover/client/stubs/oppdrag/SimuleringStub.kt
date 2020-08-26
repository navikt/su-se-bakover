package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.right
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling

object SimuleringStub : SimuleringClient {
    override fun simulerOppdrag(utbetaling: Utbetaling, utbetalingGjelder: String) =
        Simulering(
            gjelderId = "gjelderId",
            gjelderNavn = "gjelderNavn",
            datoBeregnet = idag(),
            totalBelop = 15000,
            periodeList = listOf(
                SimulertPeriode(
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = "SUP",
                            feilkonto = false,
                            forfall = 2.februar(2020),
                            utbetalesTilId = "utbetalesTilId",
                            utbetalesTilNavn = "utbetalestTilNavn",
                            detaljer = listOf(
                                SimulertDetaljer(
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
        ).right()
}
