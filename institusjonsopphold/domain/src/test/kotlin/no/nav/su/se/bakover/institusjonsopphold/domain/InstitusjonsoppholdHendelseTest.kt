package no.nav.su.se.bakover.institusjonsopphold.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
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

    @Test
    fun `kan lage oppgave hendelse`() {
        val expectedOppgaveId = OppgaveId("oppgaveId")
        val expectedSakId = UUID.randomUUID()

        val instHendelse = nyInstitusjonsoppholdHendelse(sakId = expectedSakId)
        val actual = instHendelse.nyOppgaveHendelse(
            oppgaveId = expectedOppgaveId,
            tidligereHendelse = null,
            versjon = instHendelse.versjon.inc(),
            clock = fixedClock,
        )

        actual shouldBe OppgaveHendelse(
            hendelseId = actual.hendelseId,
            tidligereHendelseId = null,
            sakId = expectedSakId,
            versjon = Hendelsesversjon(3),
            hendelsestidspunkt = fixedTidspunkt,
            triggetAv = instHendelse.hendelseId,
            oppgaveId = expectedOppgaveId,
        )
    }
}
