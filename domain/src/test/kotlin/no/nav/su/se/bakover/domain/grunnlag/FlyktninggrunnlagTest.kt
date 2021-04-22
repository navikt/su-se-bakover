package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class FlyktninggrunnlagTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje`() {
        val original = Grunnlag.Flyktninggrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
        )
        original.copy(CopyArgs.Tidslinje.Full).let {
            it.id shouldNotBe original.id
            it.opprettet shouldBe original.opprettet
            it.periode shouldBe original.periode
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val original = Grunnlag.Flyktninggrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
        )
        original.copy(
            CopyArgs.Tidslinje.NyPeriode(
                periode = Periode.create(1.mai(2021), 31.desember(2021))
            )
        ).let {
            it.id shouldNotBe original.id
            it.opprettet shouldBe original.opprettet
            it.periode shouldBe Periode.create(1.mai(2021), 31.desember(2021))
        }
    }
}
