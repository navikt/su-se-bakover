package no.nav.su.se.bakover.web

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import org.junit.jupiter.api.Test
import java.time.Duration

internal class InitialDelayTest {

    @Test
    fun `starter med fem minutter og øker med tretti sekunder i Nais`() {
        val initialDelay = InitialDelay(ApplicationConfig.RuntimeEnvironment.Nais)

        initialDelay.next() shouldBe Duration.ofMinutes(5)
        initialDelay.next() shouldBe Duration.ofSeconds(330)
        initialDelay.next() shouldBe Duration.ofMinutes(6)
    }

    @Test
    fun `starter med fem sekunder og øker med fem sekunder lokalt`() {
        val initialDelay = InitialDelay(ApplicationConfig.RuntimeEnvironment.Local)

        initialDelay.next() shouldBe Duration.ofSeconds(5)
        initialDelay.next() shouldBe Duration.ofSeconds(10)
        initialDelay.next() shouldBe Duration.ofSeconds(15)
    }
}
