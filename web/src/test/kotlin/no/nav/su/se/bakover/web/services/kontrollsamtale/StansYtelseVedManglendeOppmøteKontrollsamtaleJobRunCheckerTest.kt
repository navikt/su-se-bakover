package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.oktober
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.ZoneOffset

internal class StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunCheckerTest {
    @Test
    fun `kjører hvis kriterer er oppfylt`() {
        StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
            clock = Clock.fixed(5.oktober(2022).atTime(4, 0, 0).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ordinærÅpningstidOppdrag = ApplicationConfig.OppdragConfig.createLocalConfig().ordinærÅpningstid,
            leaderPodLookup = mock { on { amITheLeader(any()) } doReturn true.right() },
            toggleService = mock { on { isEnabled(any()) } doReturn true },
            hostName = "hostname",
        ).shouldRun() shouldBe false

        StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
            clock = Clock.fixed(5.oktober(2022).atTime(4, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ordinærÅpningstidOppdrag = ApplicationConfig.OppdragConfig.createLocalConfig().ordinærÅpningstid,
            leaderPodLookup = mock { on { amITheLeader(any()) } doReturn true.right() },
            toggleService = mock { on { isEnabled(any()) } doReturn true },
            hostName = "hostname",
        ).shouldRun() shouldBe true

        StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
            clock = Clock.fixed(5.oktober(2022).atTime(19, 0, 10).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ordinærÅpningstidOppdrag = ApplicationConfig.OppdragConfig.createLocalConfig().ordinærÅpningstid,
            leaderPodLookup = mock { on { amITheLeader(any()) } doReturn true.right() },
            toggleService = mock { on { isEnabled(any()) } doReturn true },
            hostName = "hostname",
        ).shouldRun() shouldBe false

        StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
            clock = Clock.fixed(5.oktober(2022).atTime(18, 59, 59).atZone(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ordinærÅpningstidOppdrag = ApplicationConfig.OppdragConfig.createLocalConfig().ordinærÅpningstid,
            leaderPodLookup = mock { on { amITheLeader(any()) } doReturn true.right() },
            toggleService = mock { on { isEnabled(any()) } doReturn true },
            hostName = "hostname",
        ).shouldRun() shouldBe true
    }
}
