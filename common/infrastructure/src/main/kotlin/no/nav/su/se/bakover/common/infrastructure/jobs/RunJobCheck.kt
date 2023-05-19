package no.nav.su.se.bakover.common.infrastructure.jobs

import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.featuretoggle.ToggleClient
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.nais.erLeaderPod
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import java.net.InetAddress
import java.time.Clock
import java.time.LocalTime

data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val applicationConfig: ApplicationConfig,
    private val clock: Clock,
    private val toggleService: ToggleClient,
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
            client = toggleService,
            toggleName = name,
        )
    }
}

interface RunJobCheck {
    fun shouldRun(): Boolean
}

fun List<RunJobCheck>.shouldRun(): Boolean {
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
    private val client: ToggleClient,
    private val toggleName: String,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return client.isEnabled(toggleName)
    }
}
