package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import org.junit.jupiter.api.Test

internal class SimuleringTest {

    @Test
    fun bruttoYtelse() {
        simulering.bruttoYtelse() shouldBe 41274
        simulering.periodeList[0].bruttoYtelse() shouldBe 20637
        simulering.periodeList[1].bruttoYtelse() shouldBe 20637
    }

    private val simulering = Simulering(
        gjelderId = "07028820547",
        gjelderNavn = "MYGG LUR",
        datoBeregnet = idag(),
        nettoBeløp = 20638,
        periodeList = listOf(
            SimulertPeriode(
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = "07028820547",
                        utbetalesTilNavn = "MYGG LUR",
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFom = 1.januar(2020),
                                faktiskTom = 31.januar(2020),
                                konto = "4952000",
                                belop = 20637,
                                tilbakeforing = false,
                                sats = 20637,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 0,
                                klassekode = "SUUFORE",
                                klassekodeBeskrivelse = "Supplerende stønad Uføre",
                                klasseType = KlasseType.YTEL
                            ),
                            SimulertDetaljer(
                                faktiskFom = 1.januar(2020),
                                faktiskTom = 31.januar(2020),
                                konto = "0510000",
                                belop = -10318,
                                tilbakeforing = false,
                                sats = 0,
                                typeSats = "MND",
                                antallSats = 31,
                                uforegrad = 0,
                                klassekode = "FSKTSKAT",
                                klassekodeBeskrivelse = "Forskuddskatt",
                                klasseType = KlasseType.SKAT
                            )
                        )
                    )
                )
            ),
            SimulertPeriode(
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                utbetaling = listOf(
                    SimulertUtbetaling(
                        fagSystemId = UUID30.randomUUID().toString(),
                        feilkonto = false,
                        forfall = idag(),
                        utbetalesTilId = "07028820547",
                        utbetalesTilNavn = "MYGG LUR",
                        detaljer = listOf(
                            SimulertDetaljer(
                                faktiskFom = 1.februar(2020),
                                faktiskTom = 28.februar(2020),
                                konto = "4952000",
                                belop = 20637,
                                tilbakeforing = false,
                                sats = 20637,
                                typeSats = "MND",
                                antallSats = 1,
                                uforegrad = 0,
                                klassekode = "SUUFORE",
                                klassekodeBeskrivelse = "Supplerende stønad Uføre",
                                klasseType = KlasseType.YTEL
                            ),
                            SimulertDetaljer(
                                faktiskFom = 1.februar(2020),
                                faktiskTom = 28.februar(2020),
                                konto = "0510000",
                                belop = -10318,
                                tilbakeforing = false,
                                sats = 0,
                                typeSats = "MND",
                                antallSats = 31,
                                uforegrad = 0,
                                klassekode = "FSKTSKAT",
                                klassekodeBeskrivelse = "Forskuddskatt",
                                klasseType = KlasseType.SKAT
                            )
                        )
                    )
                )
            )
        )
    )
}
