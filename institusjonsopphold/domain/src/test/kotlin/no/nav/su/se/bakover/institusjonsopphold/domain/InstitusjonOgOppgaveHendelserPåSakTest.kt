package no.nav.su.se.bakover.institusjonsopphold.domain

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonOgOppgaveHendelserPåSak
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.domain.OppholdId
import no.nav.su.se.bakover.test.nyEksternInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import no.nav.su.se.bakover.test.nyOppgaveHendelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InstitusjonOgOppgaveHendelserPåSakTest {

    @Test
    fun `krever at inst & oppgave hendelser er knyttet til samme sak`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonOgOppgaveHendelserPåSak(
                InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(nyInstitusjonsoppholdHendelse())),
                listOf(nyOppgaveHendelse(triggetAv = nyInstitusjonsoppholdHendelse(sakId = UUID.randomUUID()))),
            )
        }

        assertDoesNotThrow {
            InstitusjonOgOppgaveHendelserPåSak(
                InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(nyInstitusjonsoppholdHendelse())),
                emptyList(),
            )
        }
    }

    @Test
    fun `henter inst hendelser som ikke har tilhørende oppgave hendelse`() {
        val instHendelseMedOppgave = nyInstitusjonsoppholdHendelse()
        val oppgaveHendelse = nyOppgaveHendelse(triggetAv = instHendelseMedOppgave)
        val instHendelseUtenOppgave = nyInstitusjonsoppholdHendelse(versjon = oppgaveHendelse.versjon.inc())

        InstitusjonOgOppgaveHendelserPåSak(
            InstitusjonsoppholdHendelserPåSak(nonEmptyListOf(instHendelseMedOppgave, instHendelseUtenOppgave)),
            listOf(oppgaveHendelse),
        ).hentInstHendelserSomManglerOppgave() shouldBe listOf(instHendelseUtenOppgave)
    }

    @Test
    fun `henter hendelser med samme oppholdId`() {
        val tidligereInstOppholdGruppe1 = nyInstitusjonsoppholdHendelse()
        val oppgaveHendelseGruppe1 = nyOppgaveHendelse(triggetAv = tidligereInstOppholdGruppe1)
        val nyInstOppholdGruppe1 = nyInstitusjonsoppholdHendelse(tidligereHendelse = tidligereInstOppholdGruppe1.hendelseId, versjon = oppgaveHendelseGruppe1.versjon.inc())

        val tidligereInstOppholdGruppe2 =
            nyInstitusjonsoppholdHendelse(eksternHendelse = nyEksternInstitusjonsoppholdHendelse(oppholdId = OppholdId(3)), versjon = nyInstOppholdGruppe1.versjon.inc())
        val oppgaveHendelseGruppe2 = nyOppgaveHendelse(triggetAv = tidligereInstOppholdGruppe2)
        val nyInstOppholdGruppe2 = nyInstitusjonsoppholdHendelse(
            tidligereHendelse = tidligereInstOppholdGruppe2.hendelseId,
            eksternHendelse = nyEksternInstitusjonsoppholdHendelse(oppholdId = OppholdId(3)),
            versjon = oppgaveHendelseGruppe2.versjon.inc(),
        )

        InstitusjonOgOppgaveHendelserPåSak(
            InstitusjonsoppholdHendelserPåSak(
                nonEmptyListOf(
                    tidligereInstOppholdGruppe1,
                    nyInstOppholdGruppe1,
                    tidligereInstOppholdGruppe2,
                    nyInstOppholdGruppe2,
                ),
            ),
            listOf(oppgaveHendelseGruppe1, oppgaveHendelseGruppe2),
        ).hentHendelserMedSammeOppholdId(OppholdId(2)) shouldBe Pair(
            listOf(
                tidligereInstOppholdGruppe1,
                nyInstOppholdGruppe1,
            ),
            listOf(oppgaveHendelseGruppe1),
        )
    }
}
