package no.nav.su.se.bakover.client.oppgave

import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId

interface Oppgave {
    fun opprettOppgave(nySøknadMedJournalId: NySøknadMedJournalId): Long
}
