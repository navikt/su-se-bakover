package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.innvilgetFormueVilkår
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt12000
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class StatusovergangTest {

    private val stønadsperiode = stønadsperiode2021

    private val innvilgetBeregning = beregning(
        periode = stønadsperiode.periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt12000(periode = stønadsperiode.periode),
        ),
    )

    private val avslagBeregning = beregning(
        periode = stønadsperiode.periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt(periode = stønadsperiode.periode, forventetInntekt = 1000000),
        ),
    )

    private val opprettet = søknadsbehandlingVilkårsvurdertUavklart(
        stønadsperiode = stønadsperiode,
    ).second

    private val simulering = no.nav.su.se.bakover.test.simuleringNy()

    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"), fixedTidspunkt)
    private val utbetalingId = UUID30.randomUUID()

    private val fritekstTilBrev: String = "Fritekst til brev"

    private val vilkårsvurdertInnvilget: Søknadsbehandling.Vilkårsvurdert.Innvilget =
        opprettet.tilVilkårsvurdert(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        ) as Søknadsbehandling.Vilkårsvurdert.Innvilget
    private val vilkårsvurdertAvslag: Søknadsbehandling.Vilkårsvurdert.Avslag =
        opprettet.tilVilkårsvurdert(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
        ) as Søknadsbehandling.Vilkårsvurdert.Avslag
    private val beregnetInnvilget: Søknadsbehandling.Beregnet.Innvilget =
        vilkårsvurdertInnvilget.tilBeregnet(innvilgetBeregning) as Søknadsbehandling.Beregnet.Innvilget
    private val beregnetAvslag: Søknadsbehandling.Beregnet.Avslag =
        vilkårsvurdertInnvilget.tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
    private val simulert: Søknadsbehandling.Simulert =
        beregnetInnvilget.tilSimulert(simulering)
    private val tilAttesteringInnvilget: Søknadsbehandling.TilAttestering.Innvilget =
        simulert.tilAttestering(saksbehandler, fritekstTilBrev)
    private val tilAttesteringAvslagVilkår: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning =
        vilkårsvurdertAvslag.tilAttestering(saksbehandler, fritekstTilBrev)
    private val tilAttesteringAvslagBeregning: Søknadsbehandling.TilAttestering.Avslag.MedBeregning =
        beregnetAvslag.tilAttestering(saksbehandler, fritekstTilBrev)
    private val underkjentInnvilget: Søknadsbehandling.Underkjent.Innvilget =
        tilAttesteringInnvilget.tilUnderkjent(attesteringUnderkjent)
    private val underkjentAvslagVilkår: Søknadsbehandling.Underkjent.Avslag.UtenBeregning =
        tilAttesteringAvslagVilkår.tilUnderkjent(attesteringUnderkjent)
    private val underkjentAvslagBeregning: Søknadsbehandling.Underkjent.Avslag.MedBeregning =
        tilAttesteringAvslagBeregning.tilUnderkjent(attesteringUnderkjent)
    private val iverksattInnvilget = tilAttesteringInnvilget.tilIverksatt(attestering)
    private val iverksattAvslagVilkår =
        tilAttesteringAvslagVilkår.tilIverksatt(attestering)
    private val iverksattAvslagBeregning =
        tilAttesteringAvslagBeregning.tilIverksatt(attestering)

    @Nested
    inner class TilVilkårsvurdert {
        @Test
        fun `opprettet til vilkårsvurdert innvilget`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `opprettet til vilkårsvurdert avslag`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(withVilkårIkkeVurdert()),
            ) shouldBe opprettet
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentAvslagVilkår.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagVilkår.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentAvslagVilkår.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagVilkår.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `ulovlige statusoverganger`() {
            listOf(
                tilAttesteringInnvilget,
                tilAttesteringAvslagVilkår,
                tilAttesteringAvslagBeregning,
                iverksattInnvilget,
                iverksattAvslagVilkår,
                iverksattAvslagBeregning,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
                    )
                }
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
                    )
                }
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `vilkårsvurdert innvilget til beregnet innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `vilkårsvurdert innvilget til beregnet avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `beregnet innvilget til beregnet innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `beregnet innvilget til beregnet avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `beregnet avslag til beregnet innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `beregnet avslag til beregnet avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `simulert til beregnet innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget
        }

        @Test
        fun `simulert til beregnet avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag
        }

        @Test
        fun `underkjent avslag med beregning til beregnet innvilget`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag med beregning til beregnet avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilget til beregnet innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilget til beregnet avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev).copy(attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `ulovlige statusoverganger`() {
            listOf(
                opprettet,
                vilkårsvurdertAvslag,
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                tilAttesteringInnvilget,
                underkjentAvslagVilkår,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilBeregnet { innvilgetBeregning },
                    )
                }
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilBeregnet { avslagBeregning },
                    )
                }
            }
        }
    }

    @Nested
    inner class TilSimulert {
        @Test
        fun `beregnet innvilget til kunne ikke simulere`() {
            forsøkStatusovergang(
                beregnetInnvilget,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `beregnet innvilget til simulering`() {
            forsøkStatusovergang(
                beregnetInnvilget,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.right()
        }

        @Test
        fun `simulering til kunne ikke simulere`() {
            forsøkStatusovergang(
                simulert,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `simulering til simulering`() {
            forsøkStatusovergang(
                simulert,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.right()
        }

        @Test
        fun `underkjent innvilgning  til kunne ikke simulere`() {
            forsøkStatusovergang(
                underkjentInnvilget,
                Statusovergang.TilSimulert {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                },
            ) shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        }

        @Test
        fun `underkjent innvilgning til simulering`() {
            forsøkStatusovergang(
                underkjentInnvilget,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.copy(fritekstTilBrev = "Fritekst til brev", attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering()))).right()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertAvslag,
                vilkårsvurdertInnvilget,
                beregnetAvslag,
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                tilAttesteringInnvilget,
                underkjentAvslagBeregning,
                underkjentAvslagVilkår,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilSimulert { simulering.right() },
                    )
                }
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `vilkårsvurder avslag til attestering`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagVilkår
        }

        @Test
        fun `vilkårsvurder beregning til attestering`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagBeregning
        }

        @Test
        fun `simulert til attestering`() {
            statusovergang(
                simulert,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringInnvilget
        }

        @Test
        fun `underkjent avslag vilkår til attestering`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagVilkår.copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagVilkår.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent avslag beregning til attestering`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagBeregning.copy(attesteringer = Attesteringshistorikk(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `underkjent innvilging til attestering`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringInnvilget.copy(attesteringer = Attesteringshistorikk(listOf(underkjentInnvilget.attesteringer.hentSisteAttestering())))
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                beregnetInnvilget,
                tilAttesteringInnvilget,
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        it,
                        Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
                    )
                }
            }
        }
    }

    @Nested
    inner class TilUnderkjent {
        @Test
        fun `til attestering avslag vilkår til underkjent avslag vilkår`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår,
                Statusovergang.TilUnderkjent(attesteringUnderkjent),
            ) shouldBe underkjentAvslagVilkår.right()
        }

        @Test
        fun `til attestering avslag beregning til underkjent avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilUnderkjent(attesteringUnderkjent),
            ) shouldBe underkjentAvslagBeregning.right()
        }

        @Test
        fun `til attestering innvilget til underkjent innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilUnderkjent(attesteringUnderkjent),
            ) shouldBe underkjentInnvilget.right()
        }

        @Test
        fun `til attestering avslag vilkår kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `til attestering avslag beregning kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `til attestering innvilget kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        attestant = NavIdentBruker.Attestant("sneaky"),
                        grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        kommentar = "",
                        opprettet = fixedTidspunkt
                    ),
                ),
            ) shouldBe Statusovergang.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilUnderkjent(attesteringUnderkjent),
                    )
                }
            }
        }
    }

    @Nested
    inner class TilIverksatt {
        @Test
        fun `attestert avslag vilkår til iverksatt avslag vilkår`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe iverksattAvslagVilkår.right()
        }

        @Test
        fun `attestert avslag beregning til iverksatt avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe iverksattAvslagBeregning.right()
        }

        @Test
        fun `attestert innvilget til iverksatt innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { utbetalingId.right() },
            ) shouldBe iverksattInnvilget.right()
        }

        @Test
        fun `attestert avslag vilkår, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert avslag beregning, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert innvilget, saksbehandler kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { UUID30.randomUUID().right() },
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    forsøkStatusovergang(
                        it,
                        Statusovergang.TilUnderkjent(attesteringUnderkjent),
                    )
                }
            }
        }
    }

    @Nested
    inner class OppdaterStønadsperiode {

        @Test
        fun `lovlige overganger`() {
            listOf(
                opprettet,
                vilkårsvurdertInnvilget,
                vilkårsvurdertAvslag,
                beregnetInnvilget,
                beregnetAvslag,
                simulert,
                underkjentAvslagVilkår,
                underkjentAvslagBeregning,
                underkjentInnvilget,
            ).forEach {
                assertDoesNotThrow {
                    statusovergang(
                        søknadsbehandling = it,
                        statusovergang = Statusovergang.OppdaterStønadsperiode(stønadsperiode),
                    )
                }
            }
        }

        @Test
        fun `ulovlige overganger`() {
            listOf(
                tilAttesteringAvslagBeregning,
                tilAttesteringAvslagVilkår,
                tilAttesteringInnvilget,
                iverksattAvslagBeregning,
                iverksattAvslagVilkår,
                iverksattInnvilget,
            ).forEach {
                assertThrows<StatusovergangVisitor.UgyldigStatusovergangException>("Kastet ikke exception: ${it.status}") {
                    statusovergang(
                        søknadsbehandling = it,
                        statusovergang = Statusovergang.OppdaterStønadsperiode(stønadsperiode),
                    )
                }
            }
        }

        @Test
        fun `oppdaterer perioden riktig`() {
            val opprettetMedGrunnlag = opprettet.copy(
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = Vilkår.Uførhet.Vurdert.create(
                        vurderingsperioder = nonEmptyListOf(
                            Vurderingsperiode.Uføre.create(
                                id = UUID.randomUUID(),
                                opprettet = opprettet.opprettet,
                                resultat = Resultat.Innvilget,
                                grunnlag = Grunnlag.Uføregrunnlag(
                                    periode = stønadsperiode.periode,
                                    uføregrad = Uføregrad.parse(20),
                                    forventetInntekt = 10,
                                    opprettet = fixedTidspunkt,
                                ),
                                periode = stønadsperiode.periode,
                                begrunnelse = "ok2k",
                            ),
                        ),
                    ),
                    formue = innvilgetFormueVilkår(stønadsperiode.periode),
                ),
            )

            val nyPeriode = Periode.create(1.februar(2021), 31.mars(2021))
            val actual = statusovergang(
                søknadsbehandling = opprettetMedGrunnlag,
                statusovergang = Statusovergang.OppdaterStønadsperiode(
                    Stønadsperiode.create(
                        nyPeriode,
                        "tester å oppdatere perioden",
                    ),
                ),
            )

            actual.periode shouldBe nyPeriode
            actual.vilkårsvurderinger.uføre.grunnlag.first().periode shouldBe nyPeriode
        }
    }
}
