package no.nav.su.se.bakover.domain.vilkår

import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VurderingsperiodeTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val original = Vurderingsperiode.Manuell.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen",
        )
        original.copy(
            CopyArgs.Tidslinje.Full,
        ).let { vurderingsperiodeCopy ->
            vurderingsperiodeCopy.id shouldNotBe original.id
            vurderingsperiodeCopy.opprettet shouldBe original.opprettet
            vurderingsperiodeCopy.periode shouldBe original.periode
            vurderingsperiodeCopy.grunnlag shouldBe original.grunnlag
            vurderingsperiodeCopy.periode shouldBe original.periode
            vurderingsperiodeCopy.begrunnelse shouldBe original.begrunnelse
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val original = Vurderingsperiode.Manuell.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen",
        )
        original.copy(
            CopyArgs.Tidslinje.NyPeriode(periode = Periode.create(1.januar(2021), 30.april(2021))),
        ).let { vurderingsperiodeCopy ->
            vurderingsperiodeCopy.id shouldNotBe original.id
            vurderingsperiodeCopy.opprettet shouldBe original.opprettet
            vurderingsperiodeCopy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
            vurderingsperiodeCopy.begrunnelse shouldBe original.begrunnelse
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
        val a = Vurderingsperiode.Manuell.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen a",
        )

        val b = Vurderingsperiode.Manuell.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
            ),
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen b",
        )

        Tidslinje(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            objekter = listOf(a, b),
            clock = fixedClock,
        ).tidslinje.let {
            it[0].let { copy ->
                copy.id shouldNotBe a.id
                copy.id shouldNotBe b.id
                copy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                copy.begrunnelse shouldBe a.begrunnelse
                copy.grunnlag?.let { grunnlagCopy ->
                    grunnlagCopy.periode shouldBe copy.periode
                    grunnlagCopy.uføregrad shouldBe a.grunnlag!!.uføregrad
                    grunnlagCopy.forventetInntekt shouldBe a.grunnlag!!.forventetInntekt
                }
            }
            it[1].let { copy ->
                copy.id shouldNotBe a.id
                copy.id shouldNotBe b.id
                copy.periode shouldBe Periode.create(1.mai(2021), 31.desember(2021))
                copy.begrunnelse shouldBe b.begrunnelse
                copy.grunnlag?.let { grunnlagCopy ->
                    grunnlagCopy.periode shouldBe copy.periode
                    grunnlagCopy.uføregrad shouldBe b.grunnlag!!.uføregrad
                    grunnlagCopy.forventetInntekt shouldBe b.grunnlag!!.forventetInntekt
                }
            }
        }
    }

    @Test
    fun `krever samsvar med periode for grunnlag dersom det eksisterer`() {
        Vurderingsperiode.Manuell.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 500,
            ),
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen",
        ) shouldBeLeft Vurderingsperiode.Manuell.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig
    }

    @Test
    fun `kan opprettes selv om grunnlag ikke eksisterer`() {
        Vurderingsperiode.Manuell.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = null,
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            begrunnelse = "begrunnelsen",
        ).isRight() shouldBe true
    }
}
