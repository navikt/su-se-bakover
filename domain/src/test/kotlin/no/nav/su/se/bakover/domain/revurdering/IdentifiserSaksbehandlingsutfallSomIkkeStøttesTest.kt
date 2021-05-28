package no.nav.su.se.bakover.domain.revurdering

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.junit.jupiter.api.Test

internal class IdentifiserSaksbehandlingsutfallSomIkkeStøttesTest {

    @Test
    fun `identifiserer at opphør ikke skjer fra samme dato som den første i beregningen`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Avslag
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.UFØRHET)
            on { tidligsteDatoFrorAvslag() } doReturn 1.juni(2021)
        }
        val månedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
        )
    }

    @Test
    fun `identifiserer at flere vilkår har opphørt`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Avslag
            on { utledOpphørsgrunner() } doReturn listOf(mock(), mock())
            on { tidligsteDatoFrorAvslag() } doReturn 1.desember(2021)
        }
        val månedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(månedsberegningMock)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.OpphørAvFlereVilkår,
        )
    }

    @Test
    fun `identifiserer at opphør av vilkår skjer i kombinasjon med beløpsendringer`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Avslag
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.UFØRHET)
            on { tidligsteDatoFrorAvslag() } doReturn 1.juni(2021)
        }
        val tidligereMånedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock)
        }
        val nyMånedsberegningMock = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
            SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - under minstegrense`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            on { tidligsteDatoFrorAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
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
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør,
        )
    }

    @Test
    fun `identifiserer at opphør grunnet lavt beløp gjøres i kombinasjon med andre beløpsendringer - for høy inntekt`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoFrorAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
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
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør,
        )
    }

    @Test
    fun `identifiserer ingen problemer hvis alle nye måneder har for lavt beløp - for høy inntekt`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoFrorAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1, nyMånedsberegningMock2)
        }

        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = tidligereBeregningMock,
            nyBeregning = nyBeregningMock,
        ).resultat shouldBeRight Unit
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - under minstebeløp`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
            on { tidligsteDatoFrorAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 200
            on { erSumYtelseUnderMinstebeløp() } doReturn true
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
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
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør,
        )
    }

    @Test
    fun `identifiserer delvis opphør hvis første måned gir opphør og andre måneder er uendret - for høy inntekt`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
            on { utledOpphørsgrunner() } doReturn listOf(Opphørsgrunn.FOR_HØY_INNTEKT)
            on { tidligsteDatoFrorAvslag() } doReturn null
        }
        val tidligereMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 2500
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val tidligereBeregningMock = mock<Beregning>() {
            on { getMånedsberegninger() } doReturn listOf(tidligereMånedsberegningMock1, tidligereMånedsberegningMock2)
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 0
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyMånedsberegningMock2 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.desember(2021), 31.desember(2021))
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
        ).resultat shouldBeLeft setOf(
            SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør,
        )
    }

    @Test
    fun `identifiserer ingen problemer hvis det ikke er opphør`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val nyMånedsberegningMock1 = mock<Månedsberegning> {
            on { periode } doReturn Periode.create(1.november(2021), 30.november(2021))
            on { getSumYtelse() } doReturn 5000
            on { erSumYtelseUnderMinstebeløp() } doReturn false
        }
        val nyBeregningMock = mock<Beregning> {
            on { getMånedsberegninger() } doReturn listOf(nyMånedsberegningMock1)
        }
        IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
            vilkårsvurderinger = vilkårsvurderingerMock,
            tidligereBeregning = mock(),
            nyBeregning = nyBeregningMock,
        ).resultat shouldBeRight Unit
    }
}
