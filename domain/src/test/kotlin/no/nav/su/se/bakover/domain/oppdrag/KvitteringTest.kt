package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class KvitteringTest {
    @Test
    fun `kvittering for utbetalt ytelse`() {
        val k1 = Kvittering(utbetalingsstatus = Kvittering.Utbetalingsstatus.OK, originalKvittering = "")
        val k2 = Kvittering(utbetalingsstatus = Kvittering.Utbetalingsstatus.OK_MED_VARSEL, originalKvittering = "")
        val k3 = Kvittering(utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL, originalKvittering = "")
        k1.erUtbetalt() shouldBe true
        k2.erUtbetalt() shouldBe true
        k3.erUtbetalt() shouldBe false
    }
}
