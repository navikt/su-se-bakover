package no.nav.su.se.bakover.common.infrastructure.job

import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.nais.erLeaderPod
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import java.net.InetAddress
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val applicationConfig: ApplicationConfig,
    private val clock: Clock,
) {
    fun manTilFredag0600til2100(): UkedagerMellom0600Og2100 {
        return UkedagerMellom0600Og2100(
            ordinærÅpningstidOppdrag = applicationConfig.oppdrag.ordinærÅpningstid,
            clock = clock,
        )
    }

    fun leaderPod(): LeaderPod {
        return LeaderPod(leaderPodLookup = leaderPodLookup)
    }
}

interface RunJobCheck {
    fun shouldRun(): Boolean
}

fun List<RunJobCheck>.shouldRun(): Boolean {
    return map { it.shouldRun() }.all { it }
}

/**
 * Denne sørger for at den gitte jobben kjører på intervall x man-fredag mellom 06:00-21:00.
 * Typisk Åpningstid for stormaskin
 */
data class UkedagerMellom0600Og2100(
    private val ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
    private val clock: Clock,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        val zonedDateTime = ZonedDateTime.now(clock.withZone(zoneIdOslo))
        val now = zonedDateTime.toLocalTime()
        val today = zonedDateTime.dayOfWeek

        return now.isAfter(ordinærÅpningstidOppdrag.first) &&
            now.isBefore(ordinærÅpningstidOppdrag.second) &&
            today !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

data class LeaderPod(
    private val leaderPodLookup: LeaderPodLookup,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return leaderPodLookup.erLeaderPod(InetAddress.getLocalHost().hostName)
    }
}
