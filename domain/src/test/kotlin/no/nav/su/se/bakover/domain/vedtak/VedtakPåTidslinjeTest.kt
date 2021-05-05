package no.nav.su.se.bakover.domain.vedtak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class VedtakPåTidslinjeTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val original = Vedtak.VedtakPåTidslinje(
            vedtakId = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(25),
                        forventetInntekt = 100,
                    ),
                ),
            ),
        )
        original.copy(CopyArgs.Tidslinje.Full).let { vedtakPåTidslinje ->
            vedtakPåTidslinje.vedtakId shouldBe original.vedtakId
            vedtakPåTidslinje.opprettet shouldBe original.opprettet
            vedtakPåTidslinje.periode shouldBe original.periode
            vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                it.id shouldNotBe original.grunnlagsdata.uføregrunnlag[0].id
                it.periode shouldBe original.grunnlagsdata.uføregrunnlag[0].periode
                it.uføregrad shouldBe original.grunnlagsdata.uføregrunnlag[0].uføregrad
                it.forventetInntekt shouldBe original.grunnlagsdata.uføregrunnlag[0].forventetInntekt
            }
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val original = Vedtak.VedtakPåTidslinje(
            vedtakId = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            grunnlagsdata = Grunnlagsdata(
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        uføregrad = Uføregrad.parse(25),
                        forventetInntekt = 100,
                    ),
                ),
            ),
        )
        original.copy(CopyArgs.Tidslinje.NyPeriode(Periode.create(1.mai(2021), 31.juli(2021)))).let { vedtakPåTidslinje ->
            vedtakPåTidslinje.vedtakId shouldBe original.vedtakId
            vedtakPåTidslinje.opprettet shouldBe original.opprettet
            vedtakPåTidslinje.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
            vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                it.id shouldNotBe original.grunnlagsdata.uføregrunnlag[0].id
                it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                it.uføregrad shouldBe original.grunnlagsdata.uføregrunnlag[0].uføregrad
                it.forventetInntekt shouldBe original.grunnlagsdata.uføregrunnlag[0].forventetInntekt
            }
        }
    }
}
