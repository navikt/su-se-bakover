package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import java.time.ZoneOffset

/**
 * TODO jah: Baser innholdet i denne mer på beregninga
 */
fun simulering(
    beregning: Beregning = beregning()
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = "gjelderNavn",
        datoBeregnet = beregning.getOpprettet().toLocalDate(ZoneOffset.UTC),
        nettoBeløp = beregning.getSumYtelse(),
        periodeList = listOf(
            SimulertPeriode(
                fraOgMed = fixedLocalDate,
                tilOgMed = fixedLocalDate.plusDays(30),
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = "fagSystemId",
                        utbetalesTilId = fnr,
                        utbetalesTilNavn = "utbetalesTilNavn",
                        forfall = fixedLocalDate,
                        feilkonto = false,
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFraOgMed = fixedLocalDate,
                                faktiskTilOgMed = fixedLocalDate.plusDays(30),
                                konto = "konto",
                                belop = 1,
                                tilbakeforing = false,
                                sats = 2,
                                typeSats = "typeSats",
                                antallSats = 3,
                                uforegrad = 4,
                                klassekode = KlasseKode.SUUFORE,
                                klassekodeBeskrivelse = "klassekodeBeskrivelse",
                                klasseType = KlasseType.YTEL,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
