package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test

internal class SimulertUtbetalingTest {

    private val simulertUtbetaling = SimulertUtbetaling(
        fagSystemId = UUID30.randomUUID().toString(),
        feilkonto = false,
        forfall = idag(fixedClock),
        utbetalesTilId = Fnr.generer(),
        utbetalesTilNavn = "MYGG LUR",
        detaljer = emptyList(),
    )

    @Test
    fun equals() {
        simulertUtbetaling shouldBe simulertUtbetaling
        simulertUtbetaling shouldBe simulertUtbetaling.copy()

        simulertUtbetaling shouldNotBe simulertUtbetaling.copy(fagSystemId = "balony")
        simulertUtbetaling shouldNotBe simulertUtbetaling.copy(feilkonto = true)
        simulertUtbetaling shouldNotBe simulertUtbetaling.copy(utbetalesTilId = Fnr.generer())
        simulertUtbetaling shouldNotBe simulertUtbetaling.copy(utbetalesTilNavn = "et annet navn")
        simulertUtbetaling shouldNotBe simulertUtbetaling.copy(
            detaljer = listOf(
                SimulertDetaljer(
                    faktiskFraOgMed = 1.januar(2020),
                    faktiskTilOgMed = 31.januar(2020),
                    konto = "",
                    belop = 0,
                    tilbakeforing = false,
                    sats = 0,
                    typeSats = "",
                    antallSats = 0,
                    uforegrad = 0,
                    klassekode = KlasseKode.SUUFORE,
                    klassekodeBeskrivelse = "",
                    klasseType = KlasseType.YTEL,
                ),
            ),
        )
    }

    @Test
    fun `equals ignorerer forfallsdato`() {
        simulertUtbetaling shouldBe simulertUtbetaling.copy(forfall = 1.januar(2020))
    }
}
