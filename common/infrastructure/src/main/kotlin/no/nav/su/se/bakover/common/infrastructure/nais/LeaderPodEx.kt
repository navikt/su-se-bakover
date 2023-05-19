package no.nav.su.se.bakover.common.infrastructure.nais

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import java.net.InetAddress

fun LeaderPodLookup.erLeaderPod(
    hostname: String = InetAddress.getLocalHost().hostName,
): Boolean {
    return amITheLeader(hostname).getOrElse { return false }
}
