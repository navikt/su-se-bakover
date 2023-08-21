package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import økonomi.domain.kvittering.Kvittering

internal class KvitteringTest {
    @Test
    fun `kvittering for utbetalt ytelse`() {
        val k1 = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = "",
            mottattTidspunkt = fixedTidspunkt,
        )
        val k2 = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
            originalKvittering = "",
            mottattTidspunkt = fixedTidspunkt,
        )
        val k3 = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
            originalKvittering = "",
            mottattTidspunkt = fixedTidspunkt,
        )
        k1.erKvittertOk() shouldBe true
        k2.erKvittertOk() shouldBe true
        k3.erKvittertOk() shouldBe false
    }
}
