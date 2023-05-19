package no.nav.su.se.bakover.common.infrastructure.git

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class GitCommitTest {
    @Test
    fun `klarer parse nais env var streng`() {
        GitCommit.fromString("ghcr.io/navikt/su-se-bakover/su-se-bakover:34360562eae183dcf5ae22f6f2619a1d678bcd82") shouldBe GitCommit(
            "34360562eae183dcf5ae22f6f2619a1d678bcd82",
        )
    }

    @Test
    fun `støtter whitespace`() {
        GitCommit.fromString("\n\t ghcr.io/navikt/su-se-bakover/su-se-bakover:34360562eae183dcf5ae22f6f2619a1d678bcd82 \t\n") shouldBe GitCommit(
            "34360562eae183dcf5ae22f6f2619a1d678bcd82",
        )
    }
}
