package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

internal class StatusovergangTest {

    private val innvilgetBeregning = BeregningFactory.ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        sats = Sats.HØY,
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        fradragStrategy = FradragStrategy.Enslig,
    )

    private val avslagBeregning = BeregningFactory.ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        sats = Sats.HØY,
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 1000000.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        fradragStrategy = FradragStrategy.Enslig,
    )

    private val stønadsperiode =
        Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)), "begrunnelsen")
    private val opprettet = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(""),
            oppgaveId = OppgaveId(""),
        ),
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private val simulering = Simulering(
        gjelderId = FnrGenerator.random(),
        gjelderNavn = "",
        datoBeregnet = LocalDate.EPOCH,
        nettoBeløp = 2500,
        periodeList = emptyList(),
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
    private val underkjentAttestering =
        Attestering.Underkjent(NavIdentBruker.Attestant("attestant"), Attestering.Underkjent.Grunn.ANDRE_FORHOLD, "")
    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant"))
    private val utbetalingId = UUID30.randomUUID()
    private val journalførtIverksettingsteg =
        JournalføringOgBrevdistribusjon.Journalført(JournalpostId("journalpostId"))
    private val distribuertIverksettingssteg =
        JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
            journalpostId = JournalpostId("journalpostId"),
            brevbestillingId = BrevbestillingId("brevbesttilingId"),
        )
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
        tilAttesteringInnvilget.tilUnderkjent(underkjentAttestering)
    private val underkjentAvslagVilkår: Søknadsbehandling.Underkjent.Avslag.UtenBeregning =
        tilAttesteringAvslagVilkår.tilUnderkjent(underkjentAttestering)
    private val underkjentAvslagBeregning: Søknadsbehandling.Underkjent.Avslag.MedBeregning =
        tilAttesteringAvslagBeregning.tilUnderkjent(underkjentAttestering)
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
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentAvslagVilkår.fritekstTilBrev)
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentAvslagVilkår.fritekstTilBrev)
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt()),
            ) shouldBe vilkårsvurdertInnvilget.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått()),
            ) shouldBe vilkårsvurdertAvslag.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
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
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
        }

        @Test
        fun `underkjent avslag med beregning til beregnet avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentAvslagBeregning.fritekstTilBrev)
        }

        @Test
        fun `underkjent innvilget til beregnet innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { innvilgetBeregning },
            ) shouldBe beregnetInnvilget.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
        }

        @Test
        fun `underkjent innvilget til beregnet avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilBeregnet { avslagBeregning },
            ) shouldBe beregnetAvslag.medFritekstTilBrev(underkjentInnvilget.fritekstTilBrev)
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
                    Statusovergang.KunneIkkeSimulereBehandling.left()
                },
            ) shouldBe Statusovergang.KunneIkkeSimulereBehandling.left()
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
                    Statusovergang.KunneIkkeSimulereBehandling.left()
                },
            ) shouldBe Statusovergang.KunneIkkeSimulereBehandling.left()
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
                    Statusovergang.KunneIkkeSimulereBehandling.left()
                },
            ) shouldBe Statusovergang.KunneIkkeSimulereBehandling.left()
        }

        @Test
        fun `underkjent innvilgning til simulering`() {
            forsøkStatusovergang(
                underkjentInnvilget,
                Statusovergang.TilSimulert {
                    simulering.right()
                },
            ) shouldBe simulert.copy(fritekstTilBrev = "Fritekst til brev").right()
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
            ) shouldBe tilAttesteringAvslagVilkår
        }

        @Test
        fun `underkjent avslag beregning til attestering`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringAvslagBeregning
        }

        @Test
        fun `underkjent innvilging til attestering`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilAttestering(saksbehandler, fritekstTilBrev),
            ) shouldBe tilAttesteringInnvilget
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
                Statusovergang.TilUnderkjent(underkjentAttestering),
            ) shouldBe underkjentAvslagVilkår.right()
        }

        @Test
        fun `til attestering avslag beregning til underkjent avslag beregning`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning,
                Statusovergang.TilUnderkjent(underkjentAttestering),
            ) shouldBe underkjentAvslagBeregning.right()
        }

        @Test
        fun `til attestering innvilget til underkjent innvilging`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget,
                Statusovergang.TilUnderkjent(underkjentAttestering),
            ) shouldBe underkjentInnvilget.right()
        }

        @Test
        fun `til attestering avslag vilkår kan ikke underkjenne sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky")),
                Statusovergang.TilUnderkjent(
                    Attestering.Underkjent(
                        NavIdentBruker.Attestant("sneaky"),
                        Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        "",
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
                        NavIdentBruker.Attestant("sneaky"),
                        Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        "",
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
                        NavIdentBruker.Attestant("sneaky"),
                        Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                        "",
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
                        Statusovergang.TilUnderkjent(underkjentAttestering),
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
        fun `attestert avslag vilkår kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert avslag beregning kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { throw IllegalStateException() },
            ) shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `attestert innvilget kan ikke attestere sitt eget verk`() {
            forsøkStatusovergang(
                tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler(attestering.attestant.navIdent)),
                Statusovergang.TilIverksatt(
                    attestering = attestering,
                ) { UUID30.randomUUID().right() },
            ) shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.SaksbehandlerOgAttestantKanIkkeVæreSammePerson.left()
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
                        Statusovergang.TilUnderkjent(underkjentAttestering),
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
    }
}
