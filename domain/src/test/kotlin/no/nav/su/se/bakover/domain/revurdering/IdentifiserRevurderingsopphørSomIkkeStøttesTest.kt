package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.beregning.finnMerknaderForPeriode
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeDesember2021
import no.nav.su.se.bakover.test.periodeJuni2021
import no.nav.su.se.bakover.test.periodeNovember2021
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlleRevurdering
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Test

internal class IdentifiserRevurderingsopphørSomIkkeStøttesTest {

    @Test
    fun `identifiserer at opphør ikke skjer fra samme dato som den første i beregningen`() {
        val beregning = beregning(periode = Periode.create(1.mai(2021), 31.desember(2021)))
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = beregning.periode,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = periodeJuni2021),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
        ).left()
    }

    @Test
    fun `identifiserer at flere vilkår har opphørt`() {
        val beregning = beregning(periode = periodeDesember2021)
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = beregning.periode,
            vilkårsvurderinger = vilkårsvurderingerAvslåttAlleRevurdering(
                periodeDesember2021,
            ),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår,
        ).left()
    }

    @Test
    fun `identifiserer at opphør av uførevilkår skjer i kombinasjon med beløpsendringer`() {
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = periodeJuni2021),
            tidligereBeregning = beregning(
                periode = periodeDesember2021,
                uføregrunnlag = nonEmptyListOf(
                    uføregrunnlagForventetInntekt(
                        periode = periodeDesember2021,
                        forventetInntekt = 200,
                    ),
                ),
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            nyBeregning = beregning(
                periode = periodeDesember2021,
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 101.0,
                    ),
                ),
            ),
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - under minstegrense`() {
        val vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget()

        val tidligereBeregning = beregning()

        val nyBeregning = beregning(
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    arbeidsinntekt = 20750.0,
                ),
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        )

        nyBeregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderinger,
            tidligereBeregning = tidligereBeregning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - for høy inntekt`() {
        val vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget()

        val tidligereBeregning = beregning()

        val nyBeregning = beregning(
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    arbeidsinntekt = 29850.0,
                ),
                fradragsgrunnlagArbeidsinntekt(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        )

        nyBeregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderinger,
            tidligereBeregning = tidligereBeregning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer ingen problemer hvis alle nye måneder har for lavt beløp - for høy inntekt`() {
        val vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget()

        val tidligereBeregning = beregning()

        val nyBeregning = beregning(
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    arbeidsinntekt = 34000.0,
                ),
            ),
        )

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderinger,
            tidligereBeregning = tidligereBeregning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat shouldBe Unit.right()
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - under minstebeløp`() {
        val vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget()

        val tidligereBeregning = beregning()

        val nyBeregning = beregning(
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 20750.0,
                ),
            ),
        )

        nyBeregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderinger,
            tidligereBeregning = tidligereBeregning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - for høy inntekt`() {
        val vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget()

        val tidligereBeregning = beregning()

        val nyBeregning = beregning(
            fradragsgrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 34000.0,
                ),
            ),
        )

        nyBeregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderinger,
            tidligereBeregning = tidligereBeregning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer ingen problemer hvis det ikke er opphør`() {
        val beregning = beregning(periode = periodeNovember2021)

        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periode2021,
            vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(periodeDesember2021),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
            clock = fixedClock,
        ).resultat shouldBe Unit.right()
    }

    @Test
    fun `identifiserer ingen problemer ved opphør av uførevilkår med endring i forventet inntekt`() {
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periodeDesember2021,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = periodeDesember2021),
            tidligereBeregning = beregning(
                periode = periodeDesember2021,
                uføregrunnlag = nonEmptyListOf(
                    uføregrunnlagForventetInntekt(
                        periode = periodeDesember2021,
                        forventetInntekt = 200,
                    ),
                ),
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            nyBeregning = beregning(
                periode = periodeDesember2021,
                uføregrunnlag = nonEmptyListOf(
                    uføregrunnlagForventetInntekt0(
                        periode = periodeDesember2021,
                    ),
                ),
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            clock = fixedClock,
        ).resultat shouldBe Unit.right()
    }

    @Test
    fun `skal ikke kunne opphøre og legge til fradrag i kombinasjon`() {
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periodeDesember2021,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = periodeDesember2021),
            tidligereBeregning = beregning(periode = periodeDesember2021),
            nyBeregning = beregning(
                periode = periodeDesember2021,
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }

    @Test
    fun `skal ikke kunne opphøre og fjerne fradrag i kombinasjon`() {
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = periodeDesember2021,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = periodeDesember2021),
            tidligereBeregning = beregning(
                periode = periodeDesember2021,
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            nyBeregning = beregning(periode = periodeDesember2021),
            clock = fixedClock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }

    @Test
    fun `identifiserer ingen problemer ved uføre-opphør og likt fradrag med forskjellig periode`() {
        val februarOgUt2021 = Periode.create(1.februar(2021), 31.desember(2021))
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = februarOgUt2021,
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(periode = februarOgUt2021),
            tidligereBeregning = beregning(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periode2021,
                        arbeidsinntekt = 5000.0,
                    ),
                ),
            ),
            nyBeregning = beregning(
                periode = februarOgUt2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = februarOgUt2021,
                        arbeidsinntekt = 5000.0,
                    ),
                ),
            ),
            clock = fixedClock,
        ).resultat shouldBe Unit.right()
    }
}
