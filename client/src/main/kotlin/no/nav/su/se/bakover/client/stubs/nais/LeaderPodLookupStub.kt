package no.nav.su.se.bakover.client.stubs.nais

import arrow.core.Either
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil

data object LeaderPodLookupStub : LeaderPodLookup {
    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> = Either.Right(true)
}
