package no.nav.su.se.bakover.web.services

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import java.net.InetAddress

internal fun LeaderPodLookup.erLeaderPod(
    hostname: String = InetAddress.getLocalHost().hostName,
): Boolean {
    return amITheLeader(hostname).getOrHandle { return false }
}
