package no.nav.su.se.bakover.domain.vedtak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `klarer å lage tidslinje av vedtak`() {
        //  1.jan       31.mars
        //  |--u1---|-u2-|  uføre
        //  |------------|  Vedtaket

        //          1.mars   30.april
        //          |--u3----|  uføre
        //          |--------|  Vedtaket

        //       1.feb             30.mai
        //       |--u4---|-u5------|  uføre
        //       |-----------------|  Vedtaket

        // |-------------| periode

        // |--u1-|--u4--|

        val u1 = lagUføregrunnlag(1, 1.januar(2021), 28.februar(2021))
        val u2 = lagUføregrunnlag(2, 1.mars(2021), 31.mars(2021))
        val vedtak1 = lagVedtak(1, 1.januar(2021), 31.mars(2021), listOf(u1, u2))

        val u3 = lagUføregrunnlag(3, 1.mars(2021), 30.april(2021))
        val u4 = lagUføregrunnlag(4, 1.februar(2021), 31.mars(2021))
        val vedtak2 = lagVedtak(2, 1.mars(2021), 30.april(2021), listOf(u3))

        val u5 = lagUføregrunnlag(5, 1.april(2021), 31.mai(2021))
        val vedtak3 = lagVedtak(3, 1.februar(2021), 31.mai(2021), listOf(u4, u5))

        val actual = Vedtak.VedtakGrunnlagTidslinje.fromVedtak(
            listOf(vedtak1, vedtak2, vedtak3),
            Periode.create(fraOgMed = 1.januar(2021), 31.mars(2021)),
        )

        actual shouldBe Grunnlagsdata(
            uføregrunnlag = listOf(
                lagUføregrunnlagMedId(actual.uføregrunnlag.first().id, 1, 1.januar(2021), 31.januar(2021)),
                lagUføregrunnlagMedId(actual.uføregrunnlag.last().id, 4, 1.februar(2021), 31.mars(2021))
            ),
        )
    }

    @Test
    fun `klarer å lage tidslinje av grunnlagsdata`() {
        //  1.jan       31.mars
        //  |--u1---|-u2-|  uføre
        //  |------------|  Vedtaket

        //          1.mars   30.april
        //          |--u3----|  uføre
        //          |--------|  Vedtaket

        //  |------------| periode

        // |--u1----|-u3-|

        val u1 = lagUføregrunnlag(1, 1.januar(2021), 28.februar(2021))
        val u2 = lagUføregrunnlag(2, 1.mars(2021), 31.mars(2021))
        val vedtak1 = lagVedtak(1, 1.januar(2021), 31.mars(2021), listOf(u1, u2))

        val u3 = lagUføregrunnlag(3, 1.mars(2021), 30.april(2021))
        val vedtak2 = lagVedtak(2, 1.mars(2021), 30.april(2021), listOf(u3))

        val actual = Vedtak.VedtakGrunnlagTidslinje.fromVedtak(
            listOf(vedtak1, vedtak2),
            Periode.create(fraOgMed = 1.januar(2021), 31.mars(2021)),
        )

        actual shouldBe Grunnlagsdata(
            uføregrunnlag = listOf(
                lagUføregrunnlagMedId(actual.uføregrunnlag.first().id, 1, 1.januar(2021), 28.februar(2021)),
                lagUføregrunnlagMedId(actual.uføregrunnlag.last().id, 3, 1.mars(2021), 31.mars(2021))
            ),
        )
    }

    private fun lagUføregrunnlag(rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate) = Grunnlag.Uføregrunnlag(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
        periode = Periode.create(fraDato, tilDato),
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = 0,
    )

    private fun lagUføregrunnlagMedId(id: UUID, rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate) = Grunnlag.Uføregrunnlag(
        id = id,
        opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
        periode = Periode.create(fraDato, tilDato),
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = 0,
    )

    private fun lagVedtak(rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate, listGrunnlag: List<Grunnlag.Uføregrunnlag>) = Vedtak.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = mock(),
            opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(123),
            søknad = mock(),
            oppgaveId = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            beregning = mock(),
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            fritekstTilBrev = "",
            stønadsperiode = ValgtStønadsperiode(
                periode = Periode.create(fraDato, tilDato),
                begrunnelse = "begrunnelsen for perioden",
            ),
            grunnlagsdata = Grunnlagsdata(
                listGrunnlag,
            ),
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        ),
        UUID30.randomUUID(),
    )
}
