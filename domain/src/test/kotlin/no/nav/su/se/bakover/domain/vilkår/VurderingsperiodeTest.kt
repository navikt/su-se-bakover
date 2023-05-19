package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VurderingsperiodeTest {

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val original = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = år(2021),
        )
        original.copy(
            CopyArgs.Tidslinje.Full,
        ).let { vurderingsperiodeCopy ->
            vurderingsperiodeCopy.id shouldNotBe original.id
            vurderingsperiodeCopy.opprettet shouldBe original.opprettet
            vurderingsperiodeCopy.periode shouldBe original.periode
            vurderingsperiodeCopy.periode shouldBe original.periode
            vurderingsperiodeCopy.grunnlag!!.let { grunnlagCopy ->
                grunnlagCopy.id shouldNotBe original.grunnlag!!.id
                grunnlagCopy.opprettet shouldBe original.grunnlag!!.opprettet
                grunnlagCopy.periode shouldBe år(2021)
                grunnlagCopy.uføregrad shouldBe original.grunnlag!!.uføregrad
                grunnlagCopy.forventetInntekt shouldBe original.grunnlag!!.forventetInntekt
            }
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val original = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = år(2021),
        )
        original.copy(
            CopyArgs.Tidslinje.NyPeriode(periode = Periode.create(1.januar(2021), 30.april(2021))),
        ).let { vurderingsperiodeCopy ->
            vurderingsperiodeCopy.id shouldNotBe original.id
            vurderingsperiodeCopy.opprettet shouldBe original.opprettet
            vurderingsperiodeCopy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
            vurderingsperiodeCopy.grunnlag!!.let { grunnlagCopy ->
                grunnlagCopy.id shouldNotBe original.grunnlag!!.id
                grunnlagCopy.opprettet shouldBe original.grunnlag!!.opprettet
                grunnlagCopy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                grunnlagCopy.uføregrad shouldBe original.grunnlag!!.uføregrad
                grunnlagCopy.forventetInntekt shouldBe original.grunnlag!!.forventetInntekt
            }
        }
    }

    @Test
    fun `kan lage tidslinje for vurderingsperioder`() {
        val a = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = år(2021),
        )

        val b = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
            vurdering = Vurdering.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt.plus(1, ChronoUnit.DAYS),
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
            ),
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
        )

        val c = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt.plus(2, ChronoUnit.DAYS),
            vurdering = Vurdering.Innvilget,
            grunnlag = null,
            periode = desember(2021),
        )

        listOf(a, b, c).lagTidslinje()!!.krympTilPeriode(år(2021))?.let {
            it[0].let { copy ->
                copy.id shouldNotBe a.id
                copy.id shouldNotBe b.id
                copy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                copy.grunnlag!!.let { grunnlagCopy ->
                    grunnlagCopy.id shouldNotBe a.grunnlag!!.id
                    grunnlagCopy.periode shouldBe copy.periode
                    grunnlagCopy.uføregrad shouldBe a.grunnlag!!.uføregrad
                    grunnlagCopy.forventetInntekt shouldBe a.grunnlag!!.forventetInntekt
                }
            }
            it[1].let { copy ->
                copy.id shouldNotBe a.id
                copy.id shouldNotBe b.id
                copy.periode shouldBe Periode.create(1.mai(2021), 30.november(2021))
                copy.grunnlag!!.let { grunnlagCopy ->
                    grunnlagCopy.id shouldNotBe b.grunnlag!!.id
                    grunnlagCopy.periode shouldBe copy.periode
                    grunnlagCopy.uføregrad shouldBe b.grunnlag!!.uføregrad
                    grunnlagCopy.forventetInntekt shouldBe b.grunnlag!!.forventetInntekt
                }
            }
            it[2].let { copy ->
                copy.id shouldNotBe a.id
                copy.id shouldNotBe b.id
                copy.id shouldNotBe c.id
                copy.periode shouldBe desember(2021)
                copy.grunnlag shouldBe null
            }
        }
    }

    @Test
    fun `krever samsvar med periode for grunnlag dersom det eksisterer`() {
        VurderingsperiodeUføre.tryCreate(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
        ) shouldBe VurderingsperiodeUføre.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
    }

    @Test
    fun `kan opprettes selv om grunnlag ikke eksisterer`() {
        VurderingsperiodeUføre.tryCreate(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = null,
            vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
        ).isRight() shouldBe true
    }
}
