package no.nav.su.se.bakover.client.stubs.nais

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.nais.LeaderPodLookup
import no.nav.su.se.bakover.common.infrastructure.nais.LeaderPodLookupFeil

object LeaderPodLookupStub : LeaderPodLookup {
    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> = Either.Right(true)
}
