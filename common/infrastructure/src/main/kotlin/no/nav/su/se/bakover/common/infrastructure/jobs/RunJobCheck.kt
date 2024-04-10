package no.nav.su.se.bakover.common.infrastructure.jobs

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
    fun åpningstidStormaskin(): ÅpningstidStormaskin {
        return ÅpningstidStormaskin(
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

data class ÅpningstidStormaskin(
    // TODO jah: Ta inn ZonedDateTime?
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
