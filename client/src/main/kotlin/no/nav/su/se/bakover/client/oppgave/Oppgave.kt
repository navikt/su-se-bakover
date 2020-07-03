package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.se.bakover.client.ClientError

interface Oppgave {
    fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Either<ClientError, Long>
}
