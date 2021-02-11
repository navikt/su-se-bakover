package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.journal.JournalpostId

internal object EksterneIverksettingsstegEtterUtbetalingMapper {
    fun idToObject(
        iverksattJournalpostId: JournalpostId?,
        iverksattBrevbestillingId: BrevbestillingId?
    ) = when {
        iverksattJournalpostId == null && iverksattBrevbestillingId == null -> EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
        iverksattJournalpostId != null && iverksattBrevbestillingId != null -> EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
            journalpostId = iverksattJournalpostId,
            brevbestillingId = iverksattBrevbestillingId
        )
        iverksattJournalpostId != null -> EksterneIverksettingsstegEtterUtbetaling.Journalført(
            iverksattJournalpostId
        )
        else -> throw IllegalStateException("Kunne ikke bestemme eksterne iverksettingssteg for innvilgelse, iverksattJournalpostId:$iverksattJournalpostId, iverksattBrevbestillingId:$iverksattBrevbestillingId")
    }

    fun iverksattJournalpostId(e: EksterneIverksettingsstegEtterUtbetaling): JournalpostId? =
        when (e) {
            is EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering -> null
            is EksterneIverksettingsstegEtterUtbetaling.Journalført -> e.journalpostId
            is EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev -> e.journalpostId
        }

    fun iverksattBrevbestillingId(e: EksterneIverksettingsstegEtterUtbetaling): BrevbestillingId? =
        when (e) {
            is EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering,
            is EksterneIverksettingsstegEtterUtbetaling.Journalført -> null
            is EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev -> e.brevbestillingId
        }
}

internal object EksterneIverksettingsstegForAvslagMapper {
    fun idToObject(
        iverksattJournalpostId: JournalpostId?,
        iverksattBrevbestillingId: BrevbestillingId?
    ) = when {
        iverksattJournalpostId != null && iverksattBrevbestillingId != null -> EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev(
            journalpostId = iverksattJournalpostId,
            brevbestillingId = iverksattBrevbestillingId
        )
        iverksattJournalpostId != null -> EksterneIverksettingsstegForAvslag.Journalført(
            iverksattJournalpostId
        )
        else -> throw IllegalStateException("Kunne ikke bestemme eksterne iverksettingssteg for avslag, iverksattJournalpostId:$iverksattJournalpostId, iverksattBrevbestillingId:$iverksattBrevbestillingId")
    }

    fun iverksattJournalpostId(e: EksterneIverksettingsstegForAvslag): JournalpostId? =
        when (e) {
            is EksterneIverksettingsstegForAvslag.Journalført -> e.journalpostId
            is EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev -> e.journalpostId
        }

    fun iverksattBrevbestillingId(e: EksterneIverksettingsstegForAvslag): BrevbestillingId? =
        when (e) {
            is EksterneIverksettingsstegForAvslag.Journalført -> null
            is EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev -> e.brevbestillingId
        }
}
