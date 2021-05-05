package no.nav.su.se.bakover.domain.vedtak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
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
    fun `lager tidslinje for enkelt vedtak`() {
        val vedtak = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = emptyList(),
        )
        listOf(vedtak).lagTidslinje(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe listOf(
            Vedtak.VedtakPåTidslinje(
                vedtakId = vedtak.id,
                opprettet = vedtak.opprettet,
                periode = vedtak.periode,
                grunnlagsdata = vedtak.behandling.grunnlagsdata,
            ),
        )
    }

    /**
     *  |––––|      a
     *      |–––––| b
     *  |---|-----| resultat
     */
    @Test
    fun `lager tidslinje for flere vedtak`() {
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = emptyList(),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.mai(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = emptyList(),
        )
        listOf(a, b).lagTidslinje(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe listOf(
            Vedtak.VedtakPåTidslinje(
                vedtakId = a.id,
                opprettet = a.opprettet,
                periode = Periode.create(1.januar(2021), 30.april(2021)),
                grunnlagsdata = a.behandling.grunnlagsdata,
            ),
            Vedtak.VedtakPåTidslinje(
                vedtakId = b.id,
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = b.behandling.grunnlagsdata,
            ),
        )
    }

    /**
     *  |-––––-|      a
     *  |------|      u1
     *       |------| b
     *       |------| u2
     *  |----|------| resultat
     *  |-u1-|--u2--|
     */
    @Test
    fun `begrenser perioden på grunnlagene til samme perioden som vedtaket`() {
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = listOf(
                lagUføregrunnlag(
                    rekkefølge = 1,
                    fraDato = 1.januar(2021),
                    tilDato = 31.desember(2021),
                ),
            ),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.mai(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = listOf(
                lagUføregrunnlag(
                    rekkefølge = 2,
                    fraDato = 1.mai(2021),
                    tilDato = 31.desember(2021),
                ),
            ),
        )
        listOf(a, b).lagTidslinje(Periode.create(1.januar(2021), 31.desember(2021))).let { tidslinje ->
            tidslinje[0].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.vedtakId shouldBe a.id
                vedtakPåTidslinje.opprettet shouldBe a.opprettet
                vedtakPåTidslinje.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                    it.id shouldNotBe a.behandling.grunnlagsdata.uføregrunnlag[0].id
                    it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                    it.uføregrad shouldBe a.behandling.grunnlagsdata.uføregrunnlag[0].uføregrad
                    it.forventetInntekt shouldBe a.behandling.grunnlagsdata.uføregrunnlag[0].forventetInntekt
                }
            }
            tidslinje[1].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.vedtakId shouldBe b.id
                vedtakPåTidslinje.opprettet shouldBe b.opprettet
                vedtakPåTidslinje.periode shouldBe b.periode
                vedtakPåTidslinje.grunnlagsdata.uføregrunnlag[0].let {
                    it.id shouldNotBe b.behandling.grunnlagsdata.uføregrunnlag[0].id
                    it.periode shouldBe b.periode
                    it.uføregrad shouldBe b.behandling.grunnlagsdata.uføregrunnlag[0].uføregrad
                    it.forventetInntekt shouldBe b.behandling.grunnlagsdata.uføregrunnlag[0].forventetInntekt
                }
            }
        }
    }

    /**
     *  |------| a
     *  |------| u1
     *  |––––––| b
     *           u2 (fjernet)
     *  |------| resultat
     *           (ingen uføregrunnlag)
     */
    @Test
    fun `informasjon som overskrives av nyere vedtak forsvinner fra tidslinjen`() {
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = listOf(
                lagUføregrunnlag(
                    rekkefølge = 1,
                    fraDato = 1.januar(2021),
                    tilDato = 31.desember(2021),
                ),
            ),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            listGrunnlag = emptyList(),
        )

        listOf(a, b).lagTidslinje(periode = Periode.create(1.januar(2021), 31.desember(2021))) shouldBe listOf(
            Vedtak.VedtakPåTidslinje(
                vedtakId = b.id,
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = b.behandling.grunnlagsdata,
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

    private fun lagVedtak(rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate, listGrunnlag: List<Grunnlag.Uføregrunnlag>) = Vedtak.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = mock(),
            opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2222),
            søknad = mock(),
            oppgaveId = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            beregning = mock(),
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            fritekstTilBrev = "",
            stønadsperiode = Stønadsperiode.create(
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
