package no.nav.su.se.bakover.institusjonsopphold.domain

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelserPåSak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseMedOppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseUtenOppgaveId
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
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(2)),
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(1)),
                ),
            )
        }.let { it.message shouldBe "krever at hendelsene er i sortert rekkefølge" }
    }

    @Test
    fun `krever at alle hendelsene har samme sakId`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(hendelseSakId = UUID.randomUUID()),
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(hendelseSakId = UUID.randomUUID()),
                ),
            )
        }.let { it.message shouldBe "EntitetIdene eller sakIdene var ikke lik" }
    }

    @Test
    fun `krever at alle hendelsene har ulik versjon`() {
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(3)),
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(3)),
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
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(id = id),
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(id = id),
                ),
            )
        }.let { it.message shouldBe "Krever at alle hendelser har ulik versjon" }
    }

    @Test
    fun `Krever at hendelser ikke kan peke til samme tidligere hendelse`() {
        val tidligereHendelse = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        assertThrows<IllegalArgumentException> {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    nyInstitusjonsoppholdHendelseMedOppgaveId(tidligereHendelse = tidligereHendelse),
                    nyInstitusjonsoppholdHendelseMedOppgaveId(
                        tidligereHendelse = tidligereHendelse,
                        versjon = Hendelsesversjon(3),
                    ),
                ),
            )
        }.let { it.message shouldBe "Krever at hendelser ikke kan peke til samme tidligere hendelse" }
    }

    @Test
    fun `oppfyller alle init kravene`() {
        val førsteHendelseSomHarFåttOppgaveId = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        assertDoesNotThrow {
            InstitusjonsoppholdHendelserPåSak(
                hendelser = nonEmptyListOf(
                    førsteHendelseSomHarFåttOppgaveId,
                    nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(2)),
                    nyInstitusjonsoppholdHendelseMedOppgaveId(
                        tidligereHendelse = førsteHendelseSomHarFåttOppgaveId,
                        versjon = Hendelsesversjon(3),
                    ),
                ),
            )
        }
    }

    @Test
    fun `henter alle hendelsene som har behov for oppgaveId`() {
        val førsteHendelseSomTrengerOppgaveId = nyInstitusjonsoppholdHendelseUtenOppgaveId()
        val andreHendelseSomHarFåttOppgaveId = nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(2))
        val tredjeHendelseMedOppgaveId =
            nyInstitusjonsoppholdHendelseMedOppgaveId(tidligereHendelse = andreHendelseSomHarFåttOppgaveId)
        val fjerdeHendelseSomTrengerOppgaveId =
            nyInstitusjonsoppholdHendelseUtenOppgaveId(versjon = Hendelsesversjon(4))
        InstitusjonsoppholdHendelserPåSak(
            hendelser = nonEmptyListOf(
                førsteHendelseSomTrengerOppgaveId,
                andreHendelseSomHarFåttOppgaveId,
                tredjeHendelseMedOppgaveId,
                fjerdeHendelseSomTrengerOppgaveId,
            ),
        ).hentHendelserMedBehovForOppgaveId().let {
            it shouldBe listOf(førsteHendelseSomTrengerOppgaveId, fjerdeHendelseSomTrengerOppgaveId)
        }
    }
}
