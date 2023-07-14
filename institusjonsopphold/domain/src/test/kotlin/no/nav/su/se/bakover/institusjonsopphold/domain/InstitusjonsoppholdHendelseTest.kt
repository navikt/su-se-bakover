package no.nav.su.se.bakover.institusjonsopphold.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseMedOppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseUtenOppgaveId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class InstitusjonsoppholdHendelseTest {
    @Nested
    inner class EksternInstitusjonsoppholdHendelse {
        @Test
        fun `kan knytte til en sak`() {
            val expectedSakId = UUID.randomUUID()
            val eksternHendelse = nyEksternInstitusjonsoppholdHendelse()
            eksternHendelse.nyHendelseMedSak(
                expectedSakId,
                Hendelsesversjon.ny(),
                fixedClock,
            ).let {
                it.sakId shouldBe expectedSakId
                it.versjon shouldBe Hendelsesversjon(1)
                it.eksterneHendelse shouldBe eksternHendelse
            }
        }
    }

    @Nested
    inner class UtenOppgaveId {
        @Test
        fun `uten oppgave id skal alltid ha oppgave id som null`() {
            nyInstitusjonsoppholdHendelseUtenOppgaveId().oppgaveId shouldBe null
        }

        @Test
        fun `kan knytte til oppgave-id`() {
            val expectedOppgaveId = OppgaveId("oppgaveId")
            val utenOppgaveId = nyInstitusjonsoppholdHendelseUtenOppgaveId()
            utenOppgaveId.nyHendelseMedOppgaveId(expectedOppgaveId, fixedClock).oppgaveId shouldBe expectedOppgaveId
        }
    }

    @Nested
    inner class MedOppgaveId {

        @Test
        fun `kan ikke knytte til en annen oppgave`() {
            assertThrows<IllegalStateException> {
                nyInstitusjonsoppholdHendelseMedOppgaveId().nyHendelseMedOppgaveId(OppgaveId("ny oppgaveId"), fixedClock)
            }
        }
    }
}
