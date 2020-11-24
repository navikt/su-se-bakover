package no.nav.su.se.bakover.domain.nais

import arrow.core.Either

interface LeaderPodLookup {
    fun amITheLeader(electorPath: String, localHostName: String): Either<LeaderPodLookupFeil, Boolean>
}

sealed class LeaderPodLookupFeil {
    object KunneIkkeKontakteLeaderElectorContainer : LeaderPodLookupFeil()
    object UkjentSvarFraLeaderElectorContainer : LeaderPodLookupFeil()
}
