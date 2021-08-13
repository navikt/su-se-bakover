package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.periodeDesember2021
import no.nav.su.se.bakover.test.periodeJuni2021
import no.nav.su.se.bakover.test.periodeNovember2021
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttAlle
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgInnvilgetFormue
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import org.junit.jupiter.api.Test

internal class IdentifiserRevurderingsutfallSomIkkeStøttesTest {

    @Test
    fun `identifiserer at opphør ikke skjer fra samme dato som den første i beregningen`() {
        val beregning = beregning(periode = Periode.create(1.mai(2021), 31.desember(2021)))
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(periode = periodeJuni2021),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
        ).left()
    }

    @Test
    fun `identifiserer at flere vilkår har opphørt`() {
        val beregning = beregning(periode = periodeDesember2021)
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttAlle(periode = periodeDesember2021),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår,
        ).left()
    }

    @Test
    fun `identifiserer at opphør av uførevilkår skjer i kombinasjon med beløpsendringer`() {
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(periode = periodeJuni2021),
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
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - under minstegrense`() {
        // TODO jah: Umulig case - Vilkårsvurderinger inneholder bare uføre+formue (bør legge til fradrag som et vilkår)
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            on { tidligsteDatoForAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 3333
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - for høy inntekt`() {
        // TODO jah: Umulig case - Vilkårsvurderinger inneholder bare uføre+formue (bør legge til fradrag som et vilkår)
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoForAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 3333
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer ingen problemer hvis alle nye måneder har for lavt beløp - for høy inntekt`() {
        // TODO jah: Umulig case - Vilkårsvurderinger inneholder bare uføre+formue (bør legge til fradrag som et vilkår)
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoForAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
            on { alleMånederErUnderMinstebeløp() } doReturn false
            on { alleMånederHarBeløpLik0() } doReturn true
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBe Unit.right()
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - under minstebeløp`() {
        // TODO jah: Umulig case - Vilkårsvurderinger inneholder bare uføre+formue (bør legge til fradrag som et vilkår)
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            on { tidligsteDatoForAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - for høy inntekt`() {
        // TODO jah: Umulig case - Vilkårsvurderinger inneholder bare uføre+formue (bør legge til fradrag som et vilkår)
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoForAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn periodeNovember2021
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn periodeDesember2021
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.DelvisOpphør,
        ).left()
    }

    @Test
    fun `identifiserer ingen problemer hvis det ikke er opphør`() {
        val beregning = beregning(periode = periodeNovember2021)

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerInnvilget(periodeDesember2021),
            tidligereBeregning = beregning,
            nyBeregning = beregning,
        ).resultat shouldBe Unit.right()
    }

    @Test
    fun `identifiserer ingen problemer ved opphør av uførevilkår med endring i forventet inntekt`() {
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(periode = periodeJuni2021),
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
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
        ).left()
    }

    @Test
    fun `skal ikke kunne opphøre og legge til fradrag i kombinasjon`() {
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(periode = periodeDesember2021),
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
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }

    @Test
    fun `skal ikke kunne opphøre og fjerne fradrag i kombinasjon`() {
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgInnvilgetFormue(periode = periodeDesember2021),
            tidligereBeregning = beregning(
                periode = periodeDesember2021,
                fradragsgrunnlag = nonEmptyListOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periodeDesember2021,
                        arbeidsinntekt = 100.0,
                    ),
                ),
            ),
            nyBeregning = beregning(
                periode = periodeDesember2021,

            ),
        ).resultat shouldBe setOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        ).left()
    }
}
