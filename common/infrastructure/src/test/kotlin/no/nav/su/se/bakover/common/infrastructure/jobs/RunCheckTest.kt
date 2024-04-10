package no.nav.su.se.bakover.common.infrastructure.jobs

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.ZoneOffset

internal class RunCheckTest {
    @Test
    fun `sjekk åpningstid for stormaskin`() {
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(4, 0, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(4, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe true
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(19, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(18, 59, 59).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe true
        }

        // Skal ikke være åpent i helgen (lørdag)
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(25.november(2023).atTime(12, 0, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }

        // Skal ikke være åpent i helgen (søndag)
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(26.november(2023).atTime(12, 0, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }
    }

    @Test
    fun `sjekk leaderpod`() {
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
        ).let {
            it.leaderPod().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
        ).let {
            it.leaderPod().shouldRun() shouldBe true
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
        ).let {
            it.leaderPod().shouldRun() shouldBe false
        }
    }

    @Test
    fun `kombinasjon av flere`() {
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(15, 0, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ).let {
            listOf(
                it.åpningstidStormaskin(),
                it.leaderPod(),
            ).shouldRun() shouldBe true
        }
    }
}
