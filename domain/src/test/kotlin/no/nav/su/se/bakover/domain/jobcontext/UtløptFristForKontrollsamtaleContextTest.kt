package no.nav.su.se.bakover.domain.jobcontext

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtløptFristForKontrollsamtaleContextTest {
    @Test
    fun `oppdatering av verdier`() {
        UtløptFristForKontrollsamtaleContext(
            id = NameAndLocalDateId(
                jobName = "a",
                date = fixedLocalDate,
            ),
            opprettet = fixedTidspunkt,
            endret = fixedTidspunkt,
            prosessert = setOf(),
            ikkeMøtt = setOf(),
            feilet = setOf(),
        ).let {
            val a = UUID.fromString("8f307fcb-080e-49f9-8180-6bc31887c966")
            val b = UUID.fromString("f37669b7-1d0b-4423-8f0f-b694da90e90f")
            val c = UUID.fromString("436d38dc-9239-4219-9b52-538ba3cf75cc")
            val d = UUID.fromString("35f1e52c-02b6-4dbe-9060-3abc01be30e4")
            val utestående = listOf(a, b, c, d)
            it.uprosesserte { utestående } shouldBe utestående.toSet()
            it.prosessert(a, fixedClock)
                .ikkeMøtt(b, fixedClock)
                .feilet(c, "c1feil", fixedClock)
                .feilet(c, "c2feil", fixedClock).let {
                    it shouldBe UtløptFristForKontrollsamtaleContext(
                        id = NameAndLocalDateId(
                            jobName = "a",
                            date = fixedLocalDate,
                        ),
                        opprettet = fixedTidspunkt,
                        endret = fixedTidspunkt,
                        prosessert = setOf(a, b),
                        ikkeMøtt = setOf(b),
                        feilet = setOf(
                            UtløptFristForKontrollsamtaleContext.Feilet(
                                id = c,
                                retries = 1,
                                feil = "c2feil",
                            ),
                        ),
                    )
                    it
                }
                .ikkeMøtt(c, fixedClock)
                .prosessert(d, fixedClock).let {
                    it shouldBe UtløptFristForKontrollsamtaleContext(
                        id = NameAndLocalDateId(
                            jobName = "a",
                            date = fixedLocalDate,
                        ),
                        opprettet = fixedTidspunkt,
                        endret = fixedTidspunkt,
                        prosessert = setOf(a, b, c, d),
                        ikkeMøtt = setOf(b, c),
                        feilet = setOf(),
                    )
                    it
                }
        }
    }
}
