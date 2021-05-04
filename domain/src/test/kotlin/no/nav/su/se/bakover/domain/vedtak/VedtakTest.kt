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
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `klarer å lage tidslinje av vedtak`() {
        //  1.jan       31.mars
        //  |--u1---|-u2-|  uføre
        //  |-----f1-----|  flykting
        //  |------------|  Vedtaket

        //          1.mars   30.april
        //          |--u3----|  uføre
        //          |--f2----|  flykting
        //          |--------|  Vedtaket

        //       1.feb             30.mai
        //       |--u4---|-u5------|  uføre
        //                            flykting
        //       |-----------------|  Vedtaket

        // |-------------| periode

        // |--u1-|--u4--|
        // |--f1-|

        val søknadsbehandlingVedtak = Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
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
                    periode = Periode.create(1.januar(2021), 31.mars(2021)),
                    begrunnelse = "begrunnelsen for perioden",
                ),
                grunnlagsdata = Grunnlagsdata(
                    listOf(
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.januar(2021),
                                tilOgMed = 28.februar(2021),
                            ),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 0,
                        ),
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 31.mars(2021),
                            ),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 1000,
                        ),
                    ),
                    listOf(
                        Grunnlag.Flyktninggrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.januar(2021),
                                tilOgMed = 31.mars(2021),
                            ),
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            UUID30.randomUUID(),
        )

        val vedtak2 = Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
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
                    periode = Periode.create(1.mars(2021), 30.april(2021)),
                    begrunnelse = "begrunnelsen for perioden",
                ),
                grunnlagsdata = Grunnlagsdata(
                    listOf(
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(3, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 30.april(2021),
                            ),
                            uføregrad = Uføregrad.parse(50),
                            forventetInntekt = 2000,
                        ),
                    ),
                    listOf(
                        Grunnlag.Flyktninggrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 30.april(2021),
                            ),
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            UUID30.randomUUID(),
        )

        val vedtak3 = Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(fixedClock).plus(3, ChronoUnit.DAYS),
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
                    periode = Periode.create(1.februar(2021), 31.mai(2021)),
                    begrunnelse = "begrunnelsen for perioden",
                ),
                grunnlagsdata = Grunnlagsdata(
                    listOf(
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(4, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.februar(2021),
                                tilOgMed = 31.mars(2021),
                            ),
                            uføregrad = Uføregrad.parse(50),
                            forventetInntekt = 2000,
                        ),
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(5, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.april(2021),
                                tilOgMed = 31.mai(2021),
                            ),
                            uføregrad = Uføregrad.parse(25),
                            forventetInntekt = 3000,
                        ),
                    ),
                    emptyList()
                ),
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            UUID30.randomUUID(),
        )

        val actual = Vedtak.GrunnlagTidslinje.fromVedtak(
            listOf(søknadsbehandlingVedtak, vedtak2, vedtak3),
            Periode.create(fraOgMed = 1.januar(2021), 31.mars(2021)),
        )

        actual shouldBe Grunnlagsdata(
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    id = actual.uføregrunnlag.first().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                    ),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                ),
                Grunnlag.Uføregrunnlag(
                    id = actual.uføregrunnlag.last().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(4, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.mars(2021),
                    ),
                    uføregrad = Uføregrad.parse(50),
                    forventetInntekt = 2000,
                ),
            ),
            flyktninggrunnlag = listOf(
                Grunnlag.Flyktninggrunnlag(
                    id = actual.flyktninggrunnlag.first().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `klarer å lage tidslinje av grunnlagsdata`() {
        //  1.jan       31.mars
        //  |--u1---|-u2-|  uføre
        //  |-----f1-----|  flykting
        //  |------------|  Vedtaket

        //          1.mars   30.april
        //          |--u3----|  uføre
        //          |--f2----|  flykting
        //          |--------|  Vedtaket

        //  |------------| periode

        // |--u1----|-u3-|
        // |---f1---|-f2-|

        val søknadsbehandlingVedtak = Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
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
                    periode = Periode.create(1.januar(2021), 31.mars(2021)),
                    begrunnelse = "begrunnelsen for perioden",
                ),
                grunnlagsdata = Grunnlagsdata(
                    listOf(
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.januar(2021),
                                tilOgMed = 28.februar(2021),
                            ),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 0,
                        ),
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 31.mars(2021),
                            ),
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 1000,
                        ),
                    ),
                    listOf(
                        Grunnlag.Flyktninggrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.januar(2021),
                                tilOgMed = 31.mars(2021),
                            ),
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            UUID30.randomUUID(),
        )

        val vedtak2 = Vedtak.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
                id = mock(),
                opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
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
                    periode = Periode.create(1.mars(2021), 30.april(2021)),
                    begrunnelse = "begrunnelsen for perioden",
                ),
                grunnlagsdata = Grunnlagsdata(
                    listOf(
                        Grunnlag.Uføregrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(3, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 30.april(2021),
                            ),
                            uføregrad = Uføregrad.parse(50),
                            forventetInntekt = 2000,
                        ),
                    ),
                    listOf(
                        Grunnlag.Flyktninggrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
                            periode = Periode.create(
                                fraOgMed = 1.mars(2021),
                                tilOgMed = 30.april(2021),
                            ),
                        ),
                    ),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            ),
            UUID30.randomUUID(),
        )

        val actual = Vedtak.GrunnlagTidslinje.fromVedtak(
            listOf(søknadsbehandlingVedtak, vedtak2),
            Periode.create(fraOgMed = 1.januar(2021), 31.mars(2021)),
        )

        actual shouldBe Grunnlagsdata(
            uføregrunnlag = listOf(
                Grunnlag.Uføregrunnlag(
                    id = actual.uføregrunnlag.first().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 28.februar(2021),
                    ),
                    uføregrad = Uføregrad.parse(100),
                    forventetInntekt = 0,
                ),
                Grunnlag.Uføregrunnlag(
                    id = actual.uføregrunnlag.last().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(3, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.mars(2021),
                        tilOgMed = 31.mars(2021),
                    ),
                    uføregrad = Uføregrad.parse(50),
                    forventetInntekt = 2000,
                ),
            ),
            flyktninggrunnlag = listOf(
                Grunnlag.Flyktninggrunnlag(
                    id = actual.flyktninggrunnlag.first().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 28.februar(2021),
                    ),
                ),
                Grunnlag.Flyktninggrunnlag(
                    id = actual.flyktninggrunnlag.last().id,
                    opprettet = Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS),
                    periode = Periode.create(
                        fraOgMed = 1.mars(2021),
                        tilOgMed = 31.mars(2021),
                    ),
                ),
            ),
        )
    }
}
