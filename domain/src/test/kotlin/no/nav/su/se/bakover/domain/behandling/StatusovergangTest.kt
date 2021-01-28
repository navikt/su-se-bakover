package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
                tilhører = FradragTilhører.BRUKER
            )
        ),
        fradragStrategy = FradragStrategy.Enslig
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
                tilhører = FradragTilhører.BRUKER
            )
        ),
        fradragStrategy = FradragStrategy.Enslig
    )

    private val opprettet = Søknadsbehandling.Opprettet(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(1),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(""),
            oppgaveId = OppgaveId("")

        ),
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random()
    )

    private val simulering = Simulering(
        gjelderId = FnrGenerator.random(),
        gjelderNavn = "",
        datoBeregnet = LocalDate.EPOCH,
        nettoBeløp = 2500,
        periodeList = emptyList()
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("")
    private val underkjentAttestering =
        Attestering.Underkjent(NavIdentBruker.Attestant(""), Attestering.Underkjent.Grunn.ANDRE_FORHOLD, "")
    private val attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(""))

    private val vilkårsvurdertInnvilget: Søknadsbehandling.Vilkårsvurdert.Innvilget =
        opprettet.tilVilkårsvurdert(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
        ) as Søknadsbehandling.Vilkårsvurdert.Innvilget
    private val vilkårsvurdertAvslag: Søknadsbehandling.Vilkårsvurdert.Avslag =
        opprettet.tilVilkårsvurdert(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()
        ) as Søknadsbehandling.Vilkårsvurdert.Avslag
    private val beregnetInnvilget: Søknadsbehandling.Beregnet.Innvilget =
        vilkårsvurdertInnvilget.tilBeregnet(innvilgetBeregning) as Søknadsbehandling.Beregnet.Innvilget
    private val beregnetAvslag: Søknadsbehandling.Beregnet.Avslag =
        vilkårsvurdertInnvilget.tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
    private val simulert: Søknadsbehandling.Simulert =
        beregnetInnvilget.tilSimulert(simulering)
    private val tilAttesteringInnvilget: Søknadsbehandling.TilAttestering.Innvilget =
        simulert.tilAttestering(saksbehandler)
    private val tilAttesteringAvslagVilkår: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning =
        vilkårsvurdertAvslag.tilAttestering(saksbehandler)
    private val tilAttesteringAvslagBeregning: Søknadsbehandling.TilAttestering.Avslag.MedBeregning =
        beregnetAvslag.tilAttestering(NavIdentBruker.Saksbehandler(""))
    private val underkjentInnvilget: Søknadsbehandling.Underkjent.Innvilget =
        tilAttesteringInnvilget.tilUnderkjent(underkjentAttestering)
    private val underkjentAvslagVilkår: Søknadsbehandling.Underkjent.Avslag.UtenBeregning =
        tilAttesteringAvslagVilkår.tilUnderkjent(underkjentAttestering)
    private val underkjentAvslagBeregning: Søknadsbehandling.Underkjent.Avslag.MedBeregning =
        tilAttesteringAvslagBeregning.tilUnderkjent(underkjentAttestering)
    private val iverksattInnvilget = tilAttesteringInnvilget.tilIverksatt(attestering)
    private val iverksattAvslagVilkår = tilAttesteringAvslagVilkår.tilIverksatt(attestering)
    private val iverksattAvslagBeregning = tilAttesteringAvslagBeregning.tilIverksatt(attestering)

    @Nested
    inner class TilVilkårsvurdert {
        @Test
        fun `opprettet til vilkårsvurdert innvilget`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `opprettet til vilkårsvurdert avslag`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `opprettet til vilkårsvurdert uavklart (opprettet)`() {
            statusovergang(
                opprettet,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårIkkeVurdert())
            ) shouldBe opprettet
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `vilkårsvurdert innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `vilkårsvurdert avslag til vilkårsvurdert avslag`() {
            statusovergang(
                vilkårsvurdertAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `beregnet innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert innvilget`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `beregnet avslag til vilkårsvurdert avslag`() {
            statusovergang(
                beregnetAvslag,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `simulert til vilkårsvurdert innvilget`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `simulert til vilkårsvurdert avslag`() {
            statusovergang(
                simulert,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `underkjent innvilget til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentInnvilget,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `underkjent avslag vilkår til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagVilkår,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert innvilget`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
            ) shouldBe vilkårsvurdertInnvilget
        }

        @Test
        fun `underkjent avslag beregning til vilkårsvurdert avslag`() {
            statusovergang(
                underkjentAvslagBeregning,
                Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
            ) shouldBe vilkårsvurdertAvslag
        }

        @Test
        fun `til attestering til vilkårsvurdert er ikke lov`() {
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringInnvilget,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringInnvilget,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringAvslagVilkår,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringAvslagVilkår,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringAvslagBeregning,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    tilAttesteringAvslagBeregning,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
        }

        @Test
        fun `iverksatt til vilkårsvurdert er ikke lov`() {
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattInnvilget,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattInnvilget,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattAvslagVilkår,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattAvslagVilkår,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattAvslagBeregning,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withAlleVilkårOppfylt())
                )
            }
            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                statusovergang(
                    iverksattAvslagBeregning,
                    Statusovergang.TilVilkårsvurdert(Behandlingsinformasjon().withVilkårAvslått())
                )
            }
        }
    }

    @Nested
    inner class Beregnet {
        @Test
        fun `vilkårsvurdert innvilget til beregnet`() {
        }
    }
}
