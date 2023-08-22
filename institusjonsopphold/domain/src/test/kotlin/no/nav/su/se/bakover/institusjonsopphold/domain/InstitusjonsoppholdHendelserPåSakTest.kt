package no.nav.su.se.bakover.institusjonsopphold.domain

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InstitusjonsoppholdHendelserPåSakTest {

    @Test
    fun `krever at hendelsene er sortert`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(2)),
                ),
            )
        }.let { it.message shouldBe "krever at hendelsene er i sortert rekkefølge" }
    }

    @Test
    fun `krever at alle hendelsene har samme sakId`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelse(sakId = UUID.randomUUID()),
                    nyInstitusjonsoppholdHendelse(sakId = UUID.randomUUID()),
                ),
            )
        }.let { it.message shouldBe "EntitetIdene eller sakIdene var ikke lik" }
    }

    @Test
    fun `krever at alle hendelsene har ulik versjon`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(3)),
                ),
            )
        }.let { it.message shouldBe "Krever at alle hendelser har ulik versjon" }
    }

    @Test
    fun `krever at alle hendelsene har ulik id`() {
        val id = HendelseId.fromUUID(UUID.randomUUID())
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelse(id = id),
                    nyInstitusjonsoppholdHendelse(id = id),
                ),
            )
        }.let { it.message shouldBe "Krever at alle hendelser har ulik versjon" }
    }

    @Test
    fun `Krever at hendelser ikke kan peke til samme tidligere hendelse`() {
        val tidligereHendelse = nyInstitusjonsoppholdHendelse()
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelse(tidligereHendelse = tidligereHendelse.hendelseId),
                    nyInstitusjonsoppholdHendelse(
                        tidligereHendelse = tidligereHendelse.hendelseId,
                        versjon = Hendelsesversjon(3),
                    ),
                ),
            )
        }.let { it.message shouldBe "Krever at hendelser ikke kan peke til samme tidligere hendelse" }
    }

    @Test
    fun `oppfyller alle init kravene`() {
        val førsteHendelseSomHarFåttOppgaveId = nyInstitusjonsoppholdHendelse()
        assertDoesNotThrow {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    førsteHendelseSomHarFåttOppgaveId,
                    nyInstitusjonsoppholdHendelse(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelse(
                        tidligereHendelse = førsteHendelseSomHarFåttOppgaveId.hendelseId,
                        versjon = Hendelsesversjon(4),
                    ),
                ),
            )
        }
    }
}
