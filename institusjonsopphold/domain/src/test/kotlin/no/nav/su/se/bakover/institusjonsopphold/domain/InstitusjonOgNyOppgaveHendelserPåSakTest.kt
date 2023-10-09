package no.nav.su.se.bakover.institusjonsopphold.domain

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonOgOppgaveHendelserPåSak
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InstitusjonOgNyOppgaveHendelserPåSakTest {

    @Test
    fun `krever at inst & oppgave hendelser er knyttet til samme sak`() {
        val institusjonsoppholdHendelse = nyInstitusjonsoppholdHendelse()
        assertThrows<IllegalArgumentException> {
            InstitusjonOgOppgaveHendelserPåSak(
                InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(institusjonsoppholdHendelse)),
                listOf(
                    nyOppgaveHendelse(
                        relaterteHendelser = listOf(
                            institusjonsoppholdHendelse.hendelseId,
                        ),
                        nesteVersjon = institusjonsoppholdHendelse.versjon.inc(),
                        // ulik sakId
                        sakId = UUID.randomUUID(),
                    ),
                ),
            )
        }

        assertDoesNotThrow {
            InstitusjonOgOppgaveHendelserPåSak(
                InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(institusjonsoppholdHendelse)),
                emptyList(),
            )
            InstitusjonOgOppgaveHendelserPåSak(
                InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(institusjonsoppholdHendelse)),
                listOf(
                    nyOppgaveHendelse(
                        relaterteHendelser = listOf(
                            institusjonsoppholdHendelse.hendelseId,
                        ),
                        nesteVersjon = institusjonsoppholdHendelse.versjon.inc(),
                        // ulik sakId
                        sakId = institusjonsoppholdHendelse.sakId,
                    ),
                ),
            )
        }
    }

    @Test
    fun `henter inst hendelser som ikke har tilhørende oppgave hendelse`() {
        val sakId = UUID.randomUUID()
        val instHendelseMedOppgave = nyInstitusjonsoppholdHendelse(
            sakId = sakId,
        )
        val oppgaveHendelse = nyOppgaveHendelse(
            relaterteHendelser = listOf(
                instHendelseMedOppgave.hendelseId,
            ),
            nesteVersjon = instHendelseMedOppgave.versjon.inc(),
            sakId = sakId,
        )
        val instHendelseUtenOppgave = nyInstitusjonsoppholdHendelse(
            eksternHendelseId = 2,
            versjon = oppgaveHendelse.versjon.inc(),
            sakId = sakId,
        )

        InstitusjonOgOppgaveHendelserPåSak(
            InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(instHendelseMedOppgave, instHendelseUtenOppgave)),
            listOf(oppgaveHendelse),
        ).hentInstHendelserSomManglerOppgave() shouldBe listOf(instHendelseUtenOppgave)
    }
}
