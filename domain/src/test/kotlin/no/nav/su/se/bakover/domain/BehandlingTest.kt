package no.nav.su.se.bakover.domain

import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.arrow.either.shouldBeLeftOfType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.BEREGNET_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.BEREGNET_INNVILGET
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.OPPRETTET
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.SIMULERT
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

internal class BehandlingTest {

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()
    private val søknad = Søknad.Journalført.MedOppgave(
        sakId = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        oppgaveId = OppgaveId("o"),
        journalpostId = JournalpostId("j")
    )
    private val behandlingFactory = BehandlingFactory(mock())

    companion object {
        val oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = UUID.randomUUID(),
            utbetalinger = emptyList()
        )
    }

    private lateinit var behandling: Behandling

    @BeforeEach
    fun beforeEach() {
        behandling = createBehandling(id1, OPPRETTET)
    }

    @Nested
    inner class Opprettet {
        private lateinit var opprettet: Behandling

        @BeforeEach
        fun beforeEach() {
            opprettet = createBehandling(id1, OPPRETTET)
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `legal operations`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt())
        }

        @Test
        fun `should update behandlingsinformasjon`() {
            val expected = extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            opprettet.oppdaterBehandlingsinformasjon(expected)
            opprettet.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `should update only specified fields in behandlingsinformasjon`() {
            val original = extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            val expected = original.patch(
                Behandlingsinformasjon(
                    formue = Behandlingsinformasjon.Formue(
                        status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                        verdier = Behandlingsinformasjon.Formue.Verdier(
                            verdiIkkePrimærbolig = 52889,
                            verdiKjøretøy = 8823,
                            innskudd = 3291,
                            verdipapir = 291,
                            pengerSkyldt = 8921,
                            kontanter = 49,
                            depositumskonto = 315177
                        ),
                        ektefellesVerdier = null,
                        begrunnelse = null
                    )
                )
            )

            val oppdatert = opprettet.oppdaterBehandlingsinformasjon(expected)

            oppdatert.behandlingsinformasjon() shouldBe expected
        }

        @Test
        fun `transition to Vilkårsvurdert`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt())
            opprettet.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `transition to Avslått`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårAvslått())
            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `dont transition if vilkårsvurdering not completed`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert())
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `skal ge tidig avslag om uførhet er ikke oppfylt`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert())
            opprettet.oppdaterBehandlingsinformasjon(
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                        1,
                        1
                    ),
                    flyktning = Behandlingsinformasjon.Flyktning(
                        Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                        null
                    )
                )
            )

            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `skal ge tidig avslag om flyktning er ikke oppfylt`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert())
            opprettet.oppdaterBehandlingsinformasjon(
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        1,
                        1
                    ),
                    flyktning = Behandlingsinformasjon.Flyktning(
                        Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
                        null
                    )
                )
            )

            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `både uførhet og flyktning må vare vurdert innen tidig avslag kan ges`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert())
            opprettet.oppdaterBehandlingsinformasjon(
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                        1,
                        1
                    )
                )
            )

            opprettet.status() shouldBe OPPRETTET

            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert())
            opprettet.oppdaterBehandlingsinformasjon(
                oppdatert = Behandlingsinformasjon(
                    flyktning = Behandlingsinformasjon.Flyktning(
                        Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
                        null
                    )
                )
            )
            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { opprettet.opprettBeregning(1.januar(2020), 31.desember(2020)) }
                .also {
                    it.msg shouldContain "Illegal operation"
                    it.msg shouldContain "opprettBeregning"
                    it.msg shouldContain "state: OPPRETTET"
                }
            assertThrows<Behandling.TilstandException> { opprettet.leggTilSimulering(defaultSimulering()) }
            assertThrows<Behandling.TilstandException> {
                opprettet.sendTilAttestering(
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                opprettet.iverksett(
                    Attestant("A123456")
                )
            }
        }
    }

    @Nested
    inner class VilkårsvurdertInnvilget {
        private lateinit var vilkårsvurdert: Behandling

        @BeforeEach
        fun beforeEach() {
            vilkårsvurdert = createBehandling(id1, OPPRETTET)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `legal operations`() {
            vilkårsvurdert.opprettBeregning(1.januar(2020), 31.desember(2020))
            vilkårsvurdert.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `should create beregning`() {
            val fraOgMed = 1.januar(2020)
            val tilOgMed = 31.desember(2020)
            vilkårsvurdert.opprettBeregning(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed
            )
            val beregning = vilkårsvurdert.beregning()!!
            beregning.getPeriode().getFraOgMed() shouldBe fraOgMed
            beregning.getPeriode().getTilOgMed() shouldBe tilOgMed
            beregning.getSats() shouldBe Sats.HØY
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt`() {
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `skal avslå hvis utbetaling er 0 for arbeidsInntekt`() {
            val periode = Periode(fraOgMed = 1.januar(2020), 31.desember(2020))
            vilkårsvurdert.opprettBeregning(
                fraOgMed = periode.getFraOgMed(),
                tilOgMed = periode.getTilOgMed(),
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = 600000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal avslå hvis utbetaling er 0 for forventetInntekt`() {
            val vilkårsvurdertInnvilget = extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            val behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt, 1, 600000)
            )
            val updatedUførhet = vilkårsvurdertInnvilget.patch(behandlingsinformasjon)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(updatedUførhet)

            vilkårsvurdert.opprettBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
            )

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal avslå hvis utbetaling er under minstebeløp`() {
            val maxUtbetaling2020 = 250116
            val periode = Periode(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020)
            )

            vilkårsvurdert.opprettBeregning(
                fraOgMed = periode.getFraOgMed(),
                tilOgMed = periode.getTilOgMed(),
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = (maxUtbetaling2020 * 0.99),
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal innvilge hvis utbetaling er nøyaktig minstebeløp`() {
            val inntektSomGirMinstebeløp = 245114.0

            val periode = Periode(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
            )

            vilkårsvurdert.opprettBeregning(
                fraOgMed = periode.getFraOgMed(),
                tilOgMed = periode.getTilOgMed(),
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        beløp = inntektSomGirMinstebeløp,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_INNVILGET
            vilkårsvurdert.beregning() shouldNotBe null
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.leggTilSimulering(defaultSimulering()) }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.sendTilAttestering(
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.iverksett(
                    Attestant("A123456")
                )
            }
        }
    }

    @Nested
    inner class VilkårsvurdertAvslag {
        private lateinit var vilkårsvurdert: Behandling

        @BeforeEach
        fun beforeEach() {
            vilkårsvurdert = createBehandling(id1, OPPRETTET)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(vilkårsvurdert).withVilkårAvslått()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `skal kunne sende til attestering`() {
            val saksbehandler = Saksbehandler("S123456")
            vilkårsvurdert.sendTilAttestering(saksbehandler)
            vilkårsvurdert.status() shouldBe TIL_ATTESTERING_AVSLAG

            vilkårsvurdert.saksbehandler() shouldBe saksbehandler
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt`() {
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.opprettBeregning(
                    1.januar(2020),
                    31.desember(2020)
                )
            }
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.leggTilSimulering(defaultSimulering()) }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.iverksett(Attestant("A123456"))
            }
        }
    }

    @Nested
    inner class Beregnet {
        private lateinit var beregnet: Behandling

        @BeforeEach
        fun beforeEach() {
            beregnet = createBehandling(id1, OPPRETTET)
            beregnet.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(beregnet).withAlleVilkårOppfylt()
            )
            beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
            beregnet.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal kunne simuleres`() {
            beregnet.leggTilSimulering(defaultSimulering())
            beregnet.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
            beregnet.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt og skal slette eksisterende beregning`() {
            beregnet.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(beregnet)
            )
            beregnet.status() shouldBe VILKÅRSVURDERT_INNVILGET
            beregnet.beregning() shouldBe null
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                beregnet.sendTilAttestering(
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                beregnet.iverksett(
                    Attestant("A123456")
                )
            }
        }

        @Nested
        inner class BeregningAvslag {
            @BeforeEach
            fun beforeEach() {
                beregnet = createBehandling(id1, OPPRETTET)
                beregnet.oppdaterBehandlingsinformasjon(
                    extractBehandlingsinformasjon(beregnet).withAlleVilkårOppfylt()
                )
                val periode = Periode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                )
                beregnet.opprettBeregning(
                    fraOgMed = periode.getFraOgMed(),
                    tilOgMed = periode.getTilOgMed(),
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            beløp = 1000000.0,
                            periode = periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )

                beregnet.status() shouldBe BEREGNET_AVSLAG
            }

            @Test
            fun `skal kunne beregne på nytt`() {
                beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
                beregnet.status() shouldBe BEREGNET_INNVILGET
            }

            @Test
            fun `skal kunne vilkårsvudere på nytt og skal slette eksisterende beregning`() {
                beregnet.oppdaterBehandlingsinformasjon(
                    extractBehandlingsinformasjon(beregnet)
                )
                beregnet.status() shouldBe VILKÅRSVURDERT_INNVILGET
                beregnet.beregning() shouldBe null
            }

            @Test
            fun `skal kunne sende til attestering`() {
                val saksbehandler = Saksbehandler("S123456")
                beregnet.sendTilAttestering(saksbehandler)

                beregnet.status() shouldBe TIL_ATTESTERING_AVSLAG
                beregnet.saksbehandler() shouldBe saksbehandler
            }

            @Test
            fun `illegal operations`() {
                assertThrows<Behandling.TilstandException> { beregnet.leggTilSimulering(defaultSimulering()) }
                assertThrows<Behandling.TilstandException> {
                    beregnet.iverksett(
                        Attestant("A123456")
                    )
                }
            }
        }
    }

    @Nested
    inner class Simulert {
        private lateinit var simulert: Behandling

        @BeforeEach
        fun beforeEach() {
            simulert = createBehandling(id1, OPPRETTET)
            simulert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(simulert)
                    .withAlleVilkårOppfylt()
            )
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.leggTilSimulering(defaultSimulering())
            simulert.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne sende til attestering`() {
            val saksbehandler = Saksbehandler("S123456")
            simulert.sendTilAttestering(saksbehandler)

            simulert.status() shouldBe TIL_ATTESTERING_INNVILGET
            simulert.saksbehandler() shouldBe saksbehandler
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal fjerne beregning hvis behandlingsinformasjon endres`() {
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.oppdaterBehandlingsinformasjon(simulert.behandlingsinformasjon())

            simulert.status() shouldBe VILKÅRSVURDERT_INNVILGET
            simulert.beregning() shouldBe null
        }

        @Test
        fun `skal kunne vilkårsvurdere på nytt`() {
            simulert.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(simulert)
                    .withAlleVilkårOppfylt()
            )
            simulert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `skal kunne simulere på nytt`() {
            simulert.leggTilSimulering(defaultSimulering())
            simulert.status() shouldBe SIMULERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                simulert.iverksett(
                    Attestant("A123456")
                )
            }
        }
    }

    @Nested
    inner class Avslått {
        private lateinit var avslått: Behandling

        @BeforeEach
        fun beforeEach() {
            avslått = createBehandling(id1, OPPRETTET)
            avslått.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(avslått).withVilkårAvslått()
            )
            avslått.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `legal operations`() {
            avslått.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(avslått).withAlleVilkårOppfylt())
            avslått.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                avslått.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                avslått.leggTilSimulering(defaultSimulering())
            }
        }
    }

    @Nested
    inner class TilAttesteringInnvilget {
        private lateinit var tilAttestering: Behandling

        @BeforeEach
        fun beforeEach() {
            tilAttestering = createBehandling(id1, OPPRETTET)
            tilAttestering.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(tilAttestering).withAlleVilkårOppfylt()
            )
            tilAttestering.opprettBeregning(1.januar(2020), 31.desember(2020))
            tilAttestering.leggTilSimulering(defaultSimulering())
            tilAttestering.sendTilAttestering(Saksbehandler("S123456"))

            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()

            tilAttestering.attestant() shouldBe null
            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `skal kunne iverksette`() {
            tilAttestering.iverksett(Attestant("A123456"))
            tilAttestering.status() shouldBe BehandlingsStatus.IVERKSATT_INNVILGET
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                tilAttestering.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.leggTilSimulering(defaultSimulering())
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(Saksbehandler("S123456"))
            }
        }
    }

    @Nested
    inner class TilAttesteringAvslag {
        private lateinit var tilAttestering: Behandling

        @BeforeEach
        fun beforeEach() {
            tilAttestering = createBehandling(id1, OPPRETTET)
            tilAttestering.oppdaterBehandlingsinformasjon(
                extractBehandlingsinformasjon(tilAttestering).withVilkårAvslått()
            )
            tilAttestering.sendTilAttestering(Saksbehandler("S123456"))
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()

            tilAttestering.attestant() shouldBe null
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `skal kunne iverksette`() {
            tilAttestering.iverksett(Attestant("A123456"))
            tilAttestering.status() shouldBe IVERKSATT_AVSLAG
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                tilAttestering.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.leggTilSimulering(defaultSimulering())
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(Saksbehandler("S123456"))
            }
        }
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ): Behandling = behandlingFactory.createBehandling(
        id = id,
        søknad = søknad,
        status = status,
        sakId = id1,
        fnr = FnrGenerator.random(),
        oppgaveId = OppgaveId("1234")
    )

    private fun defaultSimulering() = Simulering(
        gjelderId = Fnr("12345678910"),
        gjelderNavn = "NAVN NAVN",
        datoBeregnet = LocalDate.now(),
        nettoBeløp = 54600,
        periodeList = listOf()
    )
}
