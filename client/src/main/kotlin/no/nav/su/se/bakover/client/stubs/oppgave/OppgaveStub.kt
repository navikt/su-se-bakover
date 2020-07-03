package no.nav.su.se.bakover.client.stubs.oppgave

import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.se.bakover.client.oppgave.Oppgave
import kotlin.random.Random

object OppgaveStub : Oppgave {
    override fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId) = Random.nextLong()
}
