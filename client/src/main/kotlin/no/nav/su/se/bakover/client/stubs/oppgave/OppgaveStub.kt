package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.oppgave.Oppgave
import kotlin.random.Random

object OppgaveStub : Oppgave {
    override fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Either<ClientError, Long> =
        Random.nextLong().right()
}
