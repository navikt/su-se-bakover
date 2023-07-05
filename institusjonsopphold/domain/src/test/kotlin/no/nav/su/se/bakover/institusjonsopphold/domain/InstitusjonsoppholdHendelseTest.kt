package no.nav.su.se.bakover.institusjonsopphold.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.util.UUID

class InstitusjonsoppholdHendelseTest {

    @Test
    fun lol() {
        fail("")
    }

    @Nested
    inner class IkkeTilknyttetTilSak {
        @Test
        fun `kan knytte til en sak`() {
            val expectedSakId = UUID.randomUUID()
            val ikkeKnyttetTilSak = nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak()
            ikkeKnyttetTilSak.knyttTilSak(expectedSakId).sakId shouldBe expectedSakId
        }
    }

    @Nested
    inner class KnyttetTilSak {
        @Nested
        inner class UtenOppgaveId {
            @Test
            fun `kan knytte til oppgave-id`() {
                val expectedOppgaveId = OppgaveId("oppgaveId")
                val utenOppgaveId = nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId()
                utenOppgaveId.knyttTilOppgaveId(expectedOppgaveId).oppgaveId shouldBe expectedOppgaveId
            }

            @Test
            fun `kan ikke knytte til en annen sak`() {
                assertThrows<IllegalStateException> {
                    nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId().knyttTilSak(UUID.randomUUID())
                }
            }
        }

        @Nested
        inner class MedOppgaveId {
            @Test
            fun `kan ikke knytte til en annen sak`() {
                assertThrows<IllegalStateException> {
                    nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId().knyttTilSak(UUID.randomUUID())
                }
            }

            @Test
            fun `kan ikke knytte til en annen oppgave`() {
                assertThrows<IllegalStateException> {
                    nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId().knyttTilOppgaveId(OppgaveId("ny oppgaveId"))
                }
            }
        }
    }
}
