package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

val søknadId: UUID = UUID.randomUUID()
val journalpostIdSøknad = JournalpostId("journalpostIdSøknad")
val oppgaveIdSøknad = OppgaveId("oppgaveIdSøknad")

val søknadinnhold = SøknadInnholdTestdataBuilder.build()

/** Ny søknad som ikke er journalført eller laget oppgave enda*/
val nySøknad = Søknad.Ny(
    id = søknadId,
    opprettet = fixedTidspunkt,
    sakId = sakId,
    søknadInnhold = søknadinnhold,
)

val journalførtSøknad = nySøknad.journalfør(
    journalpostIdSøknad,
)

val journalførtSøknadMedOppgave = journalførtSøknad.medOppgave(
    oppgaveIdSøknad
)
