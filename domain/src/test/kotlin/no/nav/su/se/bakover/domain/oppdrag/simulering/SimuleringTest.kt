package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SimuleringTest {

    @Test
    fun bruttoYtelse() {
        simulering.bruttoYtelse() shouldBe 41274
        simulering.periodeList[0].bruttoYtelse() shouldBe 20637
        simulering.periodeList[1].bruttoYtelse() shouldBe 20637

        simulering.copy(
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    utbetaling = listOf(),
                ),
            ),
        ).bruttoYtelse() shouldBe 0
    }

    @Test
    fun equals() {
        simulering shouldBe simulering
        simulering shouldBe simulering.copy()

        simulering shouldNotBe simulering.copy(gjelderId = Fnr("10101010101"))
        simulering shouldNotBe simulering.copy(gjelderNavn = "MYGG DUM")
        simulering shouldNotBe simulering.copy(periodeList = emptyList())
    }

    @Test
    fun `equals ignorerer dato beregnet`() {
        simulering shouldBe simulering.copy(datoBeregnet = 1.januar(2020))
    }

    private val FNR = Fnr("07028820547")

    private val simulering = simulering(
        nettoBeløp = 20638,
        periodeList = listOf(
            SimulertPeriode(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                utbetaling = listOf(
                    simulertUtbetaling(
                        detaljer = listOf(
                            detalj(
                                faktiskFraOgMed = 1.januar(2020),
                                faktiskTilOgMed = 31.januar(2020),
                                belop = 20637,
                                klasseType = KlasseType.YTEL,
                            ),
                            detalj(
                                faktiskFraOgMed = 1.januar(2020),
                                faktiskTilOgMed = 31.januar(2020),
                                belop = -10318,
                                klasseType = KlasseType.SKAT,
                            ),
                        ),
                    ),
                ),
            ),
            SimulertPeriode(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                utbetaling = listOf(
                    simulertUtbetaling(
                        detaljer = listOf(
                            detalj(
                                faktiskFraOgMed = 1.februar(2020),
                                faktiskTilOgMed = 28.februar(2020),
                                belop = 20637,
                                klasseType = KlasseType.YTEL,
                            ),
                            detalj(
                                faktiskFraOgMed = 1.februar(2020),
                                faktiskTilOgMed = 28.februar(2020),
                                belop = -10318,
                                klasseType = KlasseType.SKAT,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun simulering(
        nettoBeløp: Int,
        periodeList: List<SimulertPeriode>,
    ) = Simulering(
        gjelderId = FNR,
        gjelderNavn = "MYGG LUR",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = nettoBeløp,
        periodeList,
    )

    private fun simulertUtbetaling(
        detaljer: List<SimulertDetaljer>,
    ) = SimulertUtbetaling(
        fagSystemId = UUID30.randomUUID().toString(),
        feilkonto = false,
        forfall = idag(fixedClock),
        utbetalesTilId = FNR,
        utbetalesTilNavn = "MYGG LUR",
        detaljer = detaljer,
    )

    private fun detalj(
        faktiskFraOgMed: LocalDate,
        faktiskTilOgMed: LocalDate,
        belop: Int,
        klasseType: KlasseType,
    ) = SimulertDetaljer(
        faktiskFraOgMed = faktiskFraOgMed,
        faktiskTilOgMed = faktiskTilOgMed,
        konto = "0510000",
        belop = belop,
        tilbakeforing = false,
        sats = 0,
        typeSats = "MND",
        antallSats = 31,
        uforegrad = 0,
        klassekode = if (klasseType == KlasseType.YTEL) KlasseKode.valueOf("SUUFORE") else KlasseKode.valueOf("FSKTSKAT"),
        klassekodeBeskrivelse = if (klasseType == KlasseType.YTEL) "Supplerende stønad Uføre" else "Forskuddskatt",
        klasseType = klasseType,
    )
}
