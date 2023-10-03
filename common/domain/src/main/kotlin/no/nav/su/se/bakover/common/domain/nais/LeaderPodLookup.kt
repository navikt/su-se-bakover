package no.nav.su.se.bakover.common.nais

import arrow.core.Either

interface LeaderPodLookup {
    fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean>
}

sealed interface LeaderPodLookupFeil {
    data object KunneIkkeKontakteLeaderElectorContainer : LeaderPodLookupFeil
    data object UkjentSvarFraLeaderElectorContainer : LeaderPodLookupFeil
}
