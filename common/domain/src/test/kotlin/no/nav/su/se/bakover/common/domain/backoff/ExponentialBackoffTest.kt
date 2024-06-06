package no.nav.su.se.bakover.common.domain.backoff

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.plus
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class ExponentialBackoffTest {
    @Test
    fun `Should retry when empty`() {
        Failures.EMPTY.shouldRetry(fixedClock) shouldBe Failures(
            count = 1,
            last = Tidspunkt.now(fixedClock),
        ).right()
    }

    @Test
    fun `Should not retry when 1 failure and less than a minute has passed`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(59, ChronoUnit.SECONDS)
        Failures(
            count = 1,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Unit.left()
    }

    @Test
    fun `Should retry when 1 failure and 1 minute has passed`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(1, ChronoUnit.MINUTES)
        Failures(
            count = 1,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Failures(
            count = 2,
            last = Tidspunkt.now(clockAfter),
        ).right()
    }

    @Test
    fun `Should retry when 1 failure and more than 1 minute has passed`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(1, ChronoUnit.DAYS)
        Failures(
            count = 1,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Failures(
            count = 2,
            last = Tidspunkt.now(clockAfter),
        ).right()
    }

    @Test
    fun `Should not retry when 2 failure and less than 5 minutes has passed`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(299, ChronoUnit.SECONDS)
        Failures(
            count = 2,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Unit.left()
    }

    @Test
    fun `Should retry when 2 failure and 5 minute has passed`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(5, ChronoUnit.MINUTES)
        Failures(
            count = 2,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Failures(
            count = 3,
            last = Tidspunkt.now(clockAfter),
        ).right()
    }

    @Test
    fun `should not retry if less than max value`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(1439, ChronoUnit.MINUTES)
        Failures(
            count = 11,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Unit.left()
    }

    @Test
    fun `Should support retries outside Map values`() {
        val clockBefore = fixedClock
        val clockAfter = clockBefore.plus(24, ChronoUnit.HOURS)
        Failures(
            count = 11,
            last = Tidspunkt.now(fixedClock),
        ).shouldRetry(clockAfter) shouldBe Failures(
            count = 12,
            last = Tidspunkt.now(clockAfter),
        ).right()
    }
}
