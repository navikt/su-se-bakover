package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
                    utbetaling = listOf()
                )
            )
        ).bruttoYtelse() shouldBe 0
    }

    @Test
    fun equals() {
        simulering shouldBe simulering
        simulering shouldBe simulering.copy()
        simulering shouldBe simulering.copy(datoBeregnet = 1.januar(2020))

        simulering shouldNotBe simulering.copy(gjelderId = Fnr("10101010101"))
        simulering shouldNotBe simulering.copy(gjelderNavn = "MYGG DUM")
        simulering shouldNotBe simulering.copy(nettoBeløp = 70.0)
        simulering shouldNotBe simulering.copy(periodeList = emptyList())
    }

    @Test
    fun `simulerte utbetalinger skal kun inneholde en detalj av tpen ytelse`() {
        SimuleringValidering.SimulerteUtbetalingerHarKunEnDetaljAvTypenYtelse(simulering).isValid() shouldBe true

        assertThrows<IllegalArgumentException> {
            simulering(
                nettoBeløp = 5000.0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.desember(2020),
                        utbetaling = listOf(
                            simulertUtbetaling(
                                listOf(
                                    detalj(
                                        faktiskFraOgMed = 1.januar(2020),
                                        faktiskTilOgMed = 31.januar(2020),
                                        belop = 20637.0,
                                        klasseType = KlasseType.YTEL
                                    ),
                                    detalj(
                                        faktiskFraOgMed = 1.januar(2020),
                                        faktiskTilOgMed = 31.januar(2020),
                                        belop = -5000.0,
                                        klasseType = KlasseType.YTEL
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        }

        // check "empty response" case.
        SimuleringValidering.SimulerteUtbetalingerHarKunEnDetaljAvTypenYtelse(
            simulering(
                nettoBeløp = 0.0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.desember(2020),
                        utbetaling = emptyList()
                    )
                )
            )
        ).isValid() shouldBe true
    }

    private val FNR = Fnr("07028820547")

    private val simulering = simulering(
        nettoBeløp = 20638.0,
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
                                belop = 20637.0,
                                klasseType = KlasseType.YTEL
                            ),
                            detalj(
                                faktiskFraOgMed = 1.januar(2020),
                                faktiskTilOgMed = 31.januar(2020),
                                belop = -10318.0,
                                klasseType = KlasseType.SKAT
                            )
                        )
                    )
                )
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
                                belop = 20637.0,
                                klasseType = KlasseType.YTEL
                            ),
                            detalj(
                                faktiskFraOgMed = 1.februar(2020),
                                faktiskTilOgMed = 28.februar(2020),
                                belop = -10318.0,
                                klasseType = KlasseType.SKAT
                            )
                        )
                    )
                )
            )
        )
    )

    private fun simulering(
        nettoBeløp: Double,
        periodeList: List<SimulertPeriode>
    ) = Simulering(
        gjelderId = FNR,
        gjelderNavn = "MYGG LUR",
        datoBeregnet = idag(),
        nettoBeløp = nettoBeløp,
        periodeList
    )

    private fun simulertUtbetaling(
        detaljer: List<SimulertDetaljer>
    ) = SimulertUtbetaling(
        fagSystemId = UUID30.randomUUID().toString(),
        feilkonto = false,
        forfall = idag(),
        utbetalesTilId = FNR,
        utbetalesTilNavn = "MYGG LUR",
        detaljer = detaljer
    )

    private fun detalj(
        faktiskFraOgMed: LocalDate,
        faktiskTilOgMed: LocalDate,
        belop: Double,
        klasseType: KlasseType
    ) = SimulertDetaljer(
        faktiskFraOgMed = faktiskFraOgMed,
        faktiskTilOgMed = faktiskTilOgMed,
        konto = "0510000",
        belop = belop,
        tilbakeforing = false,
        sats = 0.0,
        typeSats = "MND",
        antallSats = 31,
        uforegrad = 0,
        klassekode = if (klasseType == KlasseType.YTEL) "SUUFORE" else "FSKTSKAT",
        klassekodeBeskrivelse = if (klasseType == KlasseType.YTEL) "Supplerende stønad Uføre" else "Forskuddskatt",
        klasseType = klasseType
    )
}
