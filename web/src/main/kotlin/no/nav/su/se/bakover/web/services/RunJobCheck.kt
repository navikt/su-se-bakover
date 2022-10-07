package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.toggles.ToggleService
import java.net.InetAddress
import java.time.Clock
import java.time.LocalTime

internal data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val applicationConfig: ApplicationConfig,
    private val clock: Clock,
    private val toggleService: ToggleService,
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

    fun unleashToggle(name: String): UnleashToggle {
        return UnleashToggle(
            service = toggleService,
            toggleName = name,
        )
    }
}

internal interface RunJobCheck {
    fun shouldRun(): Boolean
}

internal fun List<RunJobCheck>.shouldRun(): Boolean {
    return map { it.shouldRun() }.all { it }
}

data class ÅpningstidStormaskin(
    private val ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
    private val clock: Clock,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return LocalTime.now(clock.withZone(zoneIdOslo)).let {
            it > ordinærÅpningstidOppdrag.first && it < ordinærÅpningstidOppdrag.second
        }
    }
}

data class LeaderPod(
    private val leaderPodLookup: LeaderPodLookup,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return leaderPodLookup.erLeaderPod(InetAddress.getLocalHost().hostName)
    }
}

data class UnleashToggle(
    private val service: ToggleService,
    private val toggleName: String,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return service.isEnabled(toggleName)
    }
}
