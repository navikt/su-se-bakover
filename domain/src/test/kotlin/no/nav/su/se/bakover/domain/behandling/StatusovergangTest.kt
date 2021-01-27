package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class StatusovergangTest {

    @Nested
    inner class TilVilkårsvurdert {
        @Test
        fun `kaster exception for ugyldige statusoverganger`() {
            val opprettet = Saksbehandling.Søknadsbehandling.Opprettet(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
                søknad = Søknad.Journalført.MedOppgave(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    sakId = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                    journalpostId = JournalpostId(""),
                    oppgaveId = OppgaveId("")

                ),
                oppgaveId = OppgaveId(""),
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                fnr = FnrGenerator.random()
            )

            assertDoesNotThrow {
                val statusovergang = Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon())
                statusovergang.visit(opprettet)
                statusovergang.get()
            }
        }
    }
}
