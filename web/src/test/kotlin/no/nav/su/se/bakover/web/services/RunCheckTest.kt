package no.nav.su.se.bakover.web.services

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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
            toggleService = mock(),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(4, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            toggleService = mock(),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe true
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(19, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            toggleService = mock(),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn false.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = Clock.fixed(5.oktober(2022).atTime(18, 59, 59).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            toggleService = mock(),
        ).let {
            it.åpningstidStormaskin().shouldRun() shouldBe true
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
            toggleService = mock(),
        ).let {
            it.leaderPod().shouldRun() shouldBe false
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
            toggleService = mock(),
        ).let {
            it.leaderPod().shouldRun() shouldBe true
        }

        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
            toggleService = mock(),
        ).let {
            it.leaderPod().shouldRun() shouldBe false
        }
    }

    @Test
    fun `sjekk unleash toggle`() {
        RunCheckFactory(
            leaderPodLookup = mock {
                on { amITheLeader(any()) } doReturn true.right()
            },
            applicationConfig = ApplicationConfig.createLocalConfig(),
            clock = fixedClock,
            toggleService = mock {
                on { isEnabled(eq("enabled")) } doReturn true
                on { isEnabled(eq("disabled")) } doReturn false
            },
        ).let {
            it.unleashToggle("enabled").shouldRun() shouldBe true
            it.unleashToggle("disabled").shouldRun() shouldBe false
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
            toggleService = mock {
                on { isEnabled(eq("enabled")) } doReturn true
                on { isEnabled(eq("disabled")) } doReturn false
            },
        ).let {
            listOf(
                it.åpningstidStormaskin(),
                it.leaderPod(),
                it.unleashToggle("enabled"),
            ).shouldRun() shouldBe true

            listOf(
                it.åpningstidStormaskin(),
                it.leaderPod(),
                it.unleashToggle("disabled"),
            ).shouldRun() shouldBe false
        }
    }
}
