package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId

internal object JournalføringOgBrevdistribusjonMapper {
    fun idToObject(
        iverksattJournalpostId: JournalpostId?,
        iverksattBrevbestillingId: BrevbestillingId?
    ) = when {
        iverksattJournalpostId == null && iverksattBrevbestillingId == null -> JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
        iverksattJournalpostId != null && iverksattBrevbestillingId != null -> JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
            journalpostId = iverksattJournalpostId,
            brevbestillingId = iverksattBrevbestillingId
        )
        iverksattJournalpostId != null -> JournalføringOgBrevdistribusjon.Journalført(
            iverksattJournalpostId
        )
        else -> throw IllegalStateException("Kunne ikke bestemme eksterne iverksettingssteg for innvilgelse, iverksattJournalpostId:$iverksattJournalpostId, iverksattBrevbestillingId:$iverksattBrevbestillingId")
    }

    fun iverksattJournalpostId(e: JournalføringOgBrevdistribusjon): JournalpostId? =
        when (e) {
            is JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert -> null
            is JournalføringOgBrevdistribusjon.Journalført -> e.journalpostId
            is JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev -> e.journalpostId
        }

    fun iverksattBrevbestillingId(e: JournalføringOgBrevdistribusjon): BrevbestillingId? =
        when (e) {
            is JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
            is JournalføringOgBrevdistribusjon.Journalført -> null
            is JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev -> e.brevbestillingId
        }
}
