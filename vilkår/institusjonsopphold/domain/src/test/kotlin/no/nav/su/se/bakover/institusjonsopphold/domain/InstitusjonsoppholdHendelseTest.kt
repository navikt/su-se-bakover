package no.nav.su.se.bakover.institusjonsopphold.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InstitusjonsoppholdHendelseTest {
    @Nested
    inner class EksternInstitusjonsoppholdHendelse {
        @Test
        fun `kan knytte til en sak`() {
            val expectedSakId = UUID.randomUUID()
            val eksternHendelse = nyEksternInstitusjonsoppholdHendelse()
            eksternHendelse.nyHendelsePåSak(
                expectedSakId,
                Hendelsesversjon.ny(),
                fixedClock,
            ).let {
                it.sakId shouldBe expectedSakId
                it.versjon shouldBe Hendelsesversjon(2)
                it.eksterneHendelse shouldBe eksternHendelse
            }
        }
    }
}
