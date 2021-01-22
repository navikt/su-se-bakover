package no.nav.su.se.bakover.domain

import arrow.core.left
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.arrow.either.shouldBeLeftOfType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
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
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.UNDERKJENT_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.UNDERKJENT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.beregning.NyBeregningForSøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.Stønadsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class BehandlingTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val behandlingId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("Z12345")
    private val heleÅret2021 = Periode.create(
        1.januar(2021),
        31.desember(2021)
    )
    private val januar2021 = Stønadsperiode.create(
        Periode.create(
            1.januar(2021),
            31.januar(2021)
        )
    )
    private val nyBeregningForSøknadsbehandling = NyBeregningForSøknadsbehandling.create(
        behandlingId = behandlingId,
        saksbehandler = saksbehandler,
        stønadsperiode = Stønadsperiode.create(heleÅret2021)
    )
    private val søknad = Søknad.Journalført.MedOppgave(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        sakId = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        oppgaveId = OppgaveId("o"),
        journalpostId = JournalpostId("j")
    )
    private val behandlingFactory = BehandlingFactory(mock(), fixedClock)

    private lateinit var behandling: Behandling

    @BeforeEach
    fun beforeEach() {
        behandling = createBehandling(behandlingId, OPPRETTET)
    }

    @Nested
    inner class Opprettet {
        private lateinit var opprettet: Behandling

        @BeforeEach
        fun beforeEach() {
            opprettet = createBehandling(behandlingId, OPPRETTET)
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `legal operations`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            )
        }

        @Test
        fun `should update behandlingsinformasjon`() {
            val expected = extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            opprettet.oppdaterBehandlingsinformasjon(saksbehandler, expected)
            opprettet.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `should update only specified fields in behandlingsinformasjon`() {
            val original = extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            val expected = original.patch(
                Behandlingsinformasjon(
                    formue = Behandlingsinformasjon.Formue(
                        status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                        borSøkerMedEPS = false,
                        verdier = Behandlingsinformasjon.Formue.Verdier(
                            verdiIkkePrimærbolig = 52889,
                            verdiEiendommer = 133337,
                            verdiKjøretøy = 8823,
                            innskudd = 3291,
                            verdipapir = 291,
                            pengerSkyldt = 8921,
                            kontanter = 49,
                            depositumskonto = 315177
                        ),
                        epsVerdier = null,
                        begrunnelse = null
                    )
                )
            )

            val oppdatert = opprettet.oppdaterBehandlingsinformasjon(saksbehandler, expected).orNull()!!

            oppdatert.behandlingsinformasjon() shouldBe expected
        }

        @Test
        fun `transition to Vilkårsvurdert`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt()
            )
            opprettet.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `transition to Avslått`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårAvslått()
            )
            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `dont transition if vilkårsvurdering not completed`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert()
            )
            opprettet.status() shouldBe OPPRETTET
        }

        @Test
        fun `skal gi tidlig avslag om uførhet er ikke oppfylt`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert()
            )
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                        1,
                        1,
                        null
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
        fun `skal gi tidlig avslag om flyktning er ikke oppfylt`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert()
            )
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                        1,
                        1,
                        null
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
        fun `både uførhet og flyktning må vare vurdert innen tidlig avslag kan gis`() {
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert()
            )
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                oppdatert = Behandlingsinformasjon(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
                        1,
                        1,
                        null
                    )
                )
            )

            opprettet.status() shouldBe OPPRETTET

            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(opprettet).withVilkårIkkeVurdert()
            )
            opprettet.oppdaterBehandlingsinformasjon(
                saksbehandler,
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
            assertThrows<Behandling.TilstandException> {
                opprettet.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
                .also {
                    it.msg shouldContain "Illegal operation"
                    it.msg shouldContain "opprettBeregning"
                    it.msg shouldContain "state: OPPRETTET"
                }
            assertThrows<Behandling.TilstandException> {
                opprettet.leggTilSimulering(
                    saksbehandler,
                    defaultSimulering()
                )
            }
            assertThrows<Behandling.TilstandException> {
                opprettet.sendTilAttestering(
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                opprettet.iverksett(Attestant("A123456"))
            }
        }
    }

    @Nested
    inner class VilkårsvurdertInnvilget {
        private lateinit var vilkårsvurdert: Behandling

        @BeforeEach
        fun beforeEach() {
            vilkårsvurdert = createBehandling(behandlingId, OPPRETTET)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `legal operations`() {
            vilkårsvurdert.opprettBeregning(nyBeregningForSøknadsbehandling)
            vilkårsvurdert.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `should create beregning`() {
            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling
            )
            val beregning = vilkårsvurdert.beregning()!!
            beregning.getPeriode().getFraOgMed() shouldBe heleÅret2021.getFraOgMed()
            beregning.getPeriode().getTilOgMed() shouldBe heleÅret2021.getTilOgMed()
            beregning.getSats() shouldBe Sats.HØY
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt`() {
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `skal avslå hvis utbetaling er 0 for arbeidsInntekt`() {
            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling.copy(
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 600000.0,
                            periode = heleÅret2021,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal avslå hvis utbetaling er 0 for forventetInntekt`() {
            val vilkårsvurdertInnvilget = extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            val behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(
                    Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    uføregrad = 1,
                    forventetInntekt = 600000,
                    begrunnelse = null
                )
            )
            val updatedUførhet = vilkårsvurdertInnvilget.patch(behandlingsinformasjon)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(saksbehandler, updatedUførhet)

            vilkårsvurdert.opprettBeregning(nyBeregningForSøknadsbehandling)

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal avslå hvis utbetaling er under minstebeløp`() {
            val maxUtbetaling2020 = 250116
            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling.copy(
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = (maxUtbetaling2020 * 0.99),
                            periode = heleÅret2021,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_AVSLAG
        }

        @Test
        fun `skal innvilge hvis utbetaling er nøyaktig minstebeløp`() {

            val inntektSomGirMinstebeløp = Sats.HØY.månedsbeløp(heleÅret2021.getFraOgMed()) * 0.98

            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling.copy(
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = inntektSomGirMinstebeløp,
                            periode = heleÅret2021,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )
            )

            vilkårsvurdert.status() shouldBe BEREGNET_INNVILGET
            vilkårsvurdert.beregning() shouldNotBe null
        }

        @Test
        fun `fradrag for forventet inntekt gir det samme månedsbeløpet uavhengig av perioden på beregningen`() {
            val vilkårsvurdertInnvilget = extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            val behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(
                    Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    uføregrad = 1,
                    forventetInntekt = 120_000, // Oppgis som årsbeløp
                    begrunnelse = null
                )
            )
            val expectedForventetInntektPerMonth = behandlingsinformasjon.uførhet!!.forventetInntekt?.div(12)

            val updatedUførhet = vilkårsvurdertInnvilget.patch(behandlingsinformasjon)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(saksbehandler, updatedUførhet)

            vilkårsvurdert.opprettBeregning(nyBeregningForSøknadsbehandling)

            vilkårsvurdert.beregning()!!.getMånedsberegninger().forEach {
                it.getFradrag().single { it.getFradragstype() == Fradragstype.ForventetInntekt }
                it.getSumFradrag() shouldBe expectedForventetInntektPerMonth
            }

            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling.copy(
                    stønadsperiode = januar2021
                )
            )

            vilkårsvurdert.beregning()!!.getMånedsberegninger().forEach {
                it.getFradrag().single { it.getFradragstype() == Fradragstype.ForventetInntekt }
                it.getSumFradrag() shouldBe expectedForventetInntektPerMonth
            }
        }

        @Test
        fun `fradrag når forventet inntekt er lik 0`() {
            val vilkårsvurdertInnvilget = extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            val behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(
                    Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    uføregrad = 1,
                    forventetInntekt = 0,
                    begrunnelse = null
                )
            )
            val expectedForventetInntektPerMonth = 0
            val updatedUførhet = vilkårsvurdertInnvilget.patch(behandlingsinformasjon)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(saksbehandler, updatedUførhet)

            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling
            )

            vilkårsvurdert.beregning()!!.getMånedsberegninger().forEach {
                it.getFradrag().single { it.getFradragstype() == Fradragstype.ForventetInntekt }
                it.getSumFradrag() shouldBe expectedForventetInntektPerMonth
            }

            vilkårsvurdert.opprettBeregning(
                nyBeregningForSøknadsbehandling.copy(
                    stønadsperiode = januar2021
                )
            )

            vilkårsvurdert.beregning()!!.getMånedsberegninger().forEach {
                it.getFradrag().single { it.getFradragstype() == Fradragstype.ForventetInntekt }
                it.getSumFradrag() shouldBe expectedForventetInntektPerMonth
            }
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.leggTilSimulering(
                    saksbehandler,
                    defaultSimulering()
                )
            }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.sendTilAttestering(
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.iverksett(Attestant("A123456"))
            }
        }
    }

    @Nested
    inner class VilkårsvurdertAvslag {
        private lateinit var vilkårsvurdert: Behandling

        @BeforeEach
        fun beforeEach() {
            vilkårsvurdert = createBehandling(behandlingId, OPPRETTET)
            vilkårsvurdert.oppdaterBehandlingsinformasjon(
                saksbehandler,
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
                saksbehandler,
                extractBehandlingsinformasjon(vilkårsvurdert).withAlleVilkårOppfylt()
            )
            vilkårsvurdert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.leggTilSimulering(
                    saksbehandler,
                    defaultSimulering()
                )
            }
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
            beregnet = createBehandling(behandlingId, OPPRETTET)
            beregnet.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(beregnet).withAlleVilkårOppfylt()
            )
            beregnet.opprettBeregning(nyBeregningForSøknadsbehandling)
            beregnet.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal kunne simuleres`() {
            beregnet.leggTilSimulering(saksbehandler, defaultSimulering())
            beregnet.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            beregnet.opprettBeregning(nyBeregningForSøknadsbehandling)
            beregnet.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal kunne vilkårsvudere på nytt og skal slette eksisterende beregning`() {
            beregnet.oppdaterBehandlingsinformasjon(
                saksbehandler,
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
                beregnet = createBehandling(behandlingId, OPPRETTET)
                beregnet.oppdaterBehandlingsinformasjon(
                    saksbehandler,
                    extractBehandlingsinformasjon(beregnet).withAlleVilkårOppfylt()
                )
                beregnet.opprettBeregning(
                    nyBeregningForSøknadsbehandling.copy(
                        fradrag = listOf(
                            FradragFactory.ny(
                                type = Fradragstype.Arbeidsinntekt,
                                månedsbeløp = 1000000.0,
                                periode = heleÅret2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER
                            )
                        )
                    )
                )

                beregnet.status() shouldBe BEREGNET_AVSLAG
            }

            @Test
            fun `skal kunne beregne på nytt`() {
                beregnet.opprettBeregning(nyBeregningForSøknadsbehandling)
                beregnet.status() shouldBe BEREGNET_INNVILGET
            }

            @Test
            fun `skal kunne vilkårsvudere på nytt og skal slette eksisterende beregning`() {
                beregnet.oppdaterBehandlingsinformasjon(
                    saksbehandler,
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
                assertThrows<Behandling.TilstandException> {
                    beregnet.leggTilSimulering(
                        saksbehandler,
                        defaultSimulering()
                    )
                }
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
            simulert = createBehandling(behandlingId, OPPRETTET)
            simulert.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(simulert)
                    .withAlleVilkårOppfylt()
            )
            simulert.opprettBeregning(nyBeregningForSøknadsbehandling)
            simulert.leggTilSimulering(saksbehandler, defaultSimulering())
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
            simulert.opprettBeregning(nyBeregningForSøknadsbehandling)
            simulert.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `skal fjerne beregning hvis behandlingsinformasjon endres`() {
            simulert.opprettBeregning(nyBeregningForSøknadsbehandling)
            simulert.oppdaterBehandlingsinformasjon(saksbehandler, simulert.behandlingsinformasjon())

            simulert.status() shouldBe VILKÅRSVURDERT_INNVILGET
            simulert.beregning() shouldBe null
        }

        @Test
        fun `skal kunne vilkårsvurdere på nytt`() {
            simulert.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(simulert)
                    .withAlleVilkårOppfylt()
            )
            simulert.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `skal kunne simulere på nytt`() {
            simulert.leggTilSimulering(saksbehandler, defaultSimulering())
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
            avslått = createBehandling(behandlingId, OPPRETTET)
            avslått.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(avslått).withVilkårAvslått()
            )
            avslått.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `legal operations`() {
            avslått.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(avslått).withAlleVilkårOppfylt()
            )
            avslått.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                avslått.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
            assertThrows<Behandling.TilstandException> {
                avslått.leggTilSimulering(saksbehandler, defaultSimulering())
            }
        }
    }

    @Nested
    inner class TilAttesteringInnvilget {
        private lateinit var tilAttestering: Behandling

        @BeforeEach
        fun beforeEach() {
            tilAttestering = createBehandling(behandlingId, OPPRETTET)
            tilAttestering.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(tilAttestering).withAlleVilkårOppfylt()
            )
            tilAttestering.opprettBeregning(nyBeregningForSøknadsbehandling)
            tilAttestering.leggTilSimulering(saksbehandler, defaultSimulering())
            tilAttestering.sendTilAttestering(Saksbehandler("S123456"))

            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `skal ikke kunne attestere sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()
            tilAttestering.underkjenn(
                Attestering.Underkjent(
                    attestant = Attestant("S123456"),
                    kommentar = "Detta skal ikke gå.",
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                )
            )
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()

            tilAttestering.attestering() shouldBe null
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
                tilAttestering.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.leggTilSimulering(saksbehandler, defaultSimulering())
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
            tilAttestering = createBehandling(behandlingId, OPPRETTET)
            tilAttestering.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(tilAttestering).withVilkårAvslått()
            )
            tilAttestering.sendTilAttestering(Saksbehandler("S123456"))
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `skal ikke kunne attestere sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()
            tilAttestering.underkjenn(
                Attestering.Underkjent(
                    attestant = Attestant("S123456"),
                    kommentar = "Detta skal ikke gå.",
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD
                )
            )
                .shouldBeLeftOfType<AttestantOgSaksbehandlerKanIkkeVæreSammePerson>()

            tilAttestering.attestering() shouldBe null
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
                tilAttestering.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.leggTilSimulering(saksbehandler, defaultSimulering())
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(Saksbehandler("S123456"))
            }
        }
    }

    @Nested
    inner class UnderkjentInnvilget {
        private lateinit var underkjent: Behandling

        @BeforeEach
        fun beforeEach() {
            underkjent = createBehandling(behandlingId, OPPRETTET)
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withAlleVilkårOppfylt()
            )
            underkjent.opprettBeregning(nyBeregningForSøknadsbehandling)
            underkjent.leggTilSimulering(saksbehandler, defaultSimulering())
            underkjent.sendTilAttestering(saksbehandler)
            underkjent.underkjenn(
                Attestering.Underkjent(
                    attestant = Attestant(navIdent = "1234"),
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "En fin kommentar om hvorfor denne innvilgelsen er feil."
                )
            )
            underkjent.status() shouldBe UNDERKJENT_INNVILGET
        }

        @Test
        fun `kan oppdatere behandlingsinformasjon`() {
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler, extractBehandlingsinformasjon(underkjent).withVilkårAvslått()
            )
            underkjent.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `kan kjøre en ny beregning`() {
            underkjent.opprettBeregning(nyBeregningForSøknadsbehandling)
            underkjent.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `kan simulere`() {
            underkjent.leggTilSimulering(saksbehandler, defaultSimulering())
            underkjent.status() shouldBe SIMULERT
        }

        @Test
        fun `kan sende til attestering`() {
            underkjent.sendTilAttestering(saksbehandler)
            underkjent.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `kan oppdatere oppgaveId`() {
            underkjent.oppdaterOppgaveId(OppgaveId("9900"))
            underkjent.oppgaveId().toString() shouldBe "9900"
        }

        @Test
        fun `kan ikke sende til attestering hvis SB og Att er samme person`() {
            val behandlingMedSammeSaksbehandlerOgAtt = underkjent.copy(
                attestering = Attestering.Underkjent(
                    attestant = Attestant(navIdent = "Z12345"),
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "Vi er den samme"
                )
            )
            behandlingMedSammeSaksbehandlerOgAtt.sendTilAttestering(saksbehandler) shouldBe AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `kan ikke utlede avslagsgrunner`() {
            assertThrows<Behandling.TilstandException> {
                underkjent.utledAvslagsgrunner()
            }
        }
    }

    @Nested
    inner class UnderkjentAvslag_vilkårsvurderinger {
        private lateinit var underkjent: Behandling

        @BeforeEach
        fun beforeEach() {
            underkjent = createBehandling(behandlingId, OPPRETTET)
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withVilkårAvslått()
            )
            underkjent.sendTilAttestering(saksbehandler)
            underkjent.underkjenn(
                Attestering.Underkjent(
                    attestant = Attestant(navIdent = "1234"),
                    grunn = Attestering.Underkjent.Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT,
                    kommentar = "En ufin kommentar om hvorfor dette avslaget er feil"
                )
            )
            underkjent.status() shouldBe UNDERKJENT_AVSLAG
        }

        @Test
        fun `kan oppdatere behandlingsinformasjon`() {
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withAlleVilkårOppfylt()
            )
            underkjent.status() shouldBe VILKÅRSVURDERT_INNVILGET
        }

        @Test
        fun `kan oppdatere behandlingsinformasjon for å gå tilbake til OPPRETTET`() {
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withAlleVilkårOppfylt().copy(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
                        uføregrad = null,
                        forventetInntekt = null,
                        begrunnelse = null
                    )
                )
            )
            underkjent.status() shouldBe OPPRETTET
        }

        @Test
        fun `kan ikke beregne`() {
            assertThrows<Behandling.TilstandException> {
                underkjent.opprettBeregning(nyBeregningForSøknadsbehandling)
            }
        }

        @Test
        fun `kan ikke simulere`() {
            assertThrows<Behandling.TilstandException> {
                underkjent.leggTilSimulering(saksbehandler, defaultSimulering())
            }
        }

        @Test
        fun `kan sende til attestering`() {
            underkjent.sendTilAttestering(saksbehandler)
            underkjent.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `kan ikke sende til attestering hvis SB og Att er samme person`() {
            val behandlingMedSammeSaksbehandlerOgAtt = underkjent.copy(
                attestering = Attestering.Underkjent(
                    attestant = Attestant(navIdent = "Z12345"),
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "Vi er den samme"
                )
            )
            behandlingMedSammeSaksbehandlerOgAtt.sendTilAttestering(saksbehandler) shouldBe AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `kan oppdatere oppgaveId`() {
            underkjent.oppdaterOppgaveId(OppgaveId("9900"))
            underkjent.oppgaveId().toString() shouldBe "9900"
        }

        @Test
        fun `kan utlede avslagsgrunner`() {
            underkjent.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.UFØRHET)
        }
    }

    @Nested
    inner class UnderkjentAvslag_Beregning {
        private lateinit var underkjent: Behandling

        @BeforeEach
        fun beforeEach() {
            underkjent = createBehandling(behandlingId, OPPRETTET)
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withAlleVilkårOppfylt()
            )
            underkjent.opprettBeregning(
                nyBeregningForSøknadsbehandling = nyBeregningForSøknadsbehandling.copy(
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.OffentligPensjon,
                            månedsbeløp = 2000000000.0,
                            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER
                        )
                    )
                )
            )
            underkjent.sendTilAttestering(saksbehandler)
            underkjent.underkjenn(
                Attestering.Underkjent(
                    attestant = Attestant(navIdent = "1234"),
                    grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
                    kommentar = "En ufin kommentar om hvorfor denne beregningen er feil"
                )
            )
            underkjent.status() shouldBe UNDERKJENT_AVSLAG
        }

        @Test
        fun `kan oppdatere behandlingsinformasjon`() {
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withVilkårAvslått()
            )
            underkjent.status() shouldBe VILKÅRSVURDERT_AVSLAG
        }

        @Test
        fun `kan oppdatere behandlingsinformasjon for å gå tilbake til OPPRETTET`() {
            underkjent.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(underkjent).withAlleVilkårOppfylt().copy(
                    uførhet = Behandlingsinformasjon.Uførhet(
                        status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
                        uføregrad = null,
                        forventetInntekt = null,
                        begrunnelse = null
                    )
                )
            )
            underkjent.status() shouldBe OPPRETTET
        }

        @Test
        fun `kan beregne`() {
            underkjent.opprettBeregning(nyBeregningForSøknadsbehandling)
            underkjent.status() shouldBe BEREGNET_INNVILGET
        }

        @Test
        fun `kan ikke simulere`() {
            assertThrows<Behandling.TilstandException> {
                underkjent.leggTilSimulering(saksbehandler, defaultSimulering())
            }
        }

        @Test
        fun `kan ikke sende til attestering hvis SB og Att er samme person`() {
            val behandlingMedSammeSaksbehandlerOgAtt = underkjent.copy(
                attestering = Attestering.Underkjent(
                    attestant = Attestant(navIdent = "Z12345"),
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "Vi er den samme"
                )
            )
            behandlingMedSammeSaksbehandlerOgAtt.sendTilAttestering(saksbehandler) shouldBe AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }

        @Test
        fun `kan oppdatere oppgaveId`() {
            underkjent.oppdaterOppgaveId(OppgaveId("9900"))
            underkjent.oppgaveId().toString() shouldBe "9900"
        }

        @Test
        fun `kan utlede avslagsgrunner`() {
            underkjent.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.FOR_HØY_INNTEKT)
        }
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ): Behandling = behandlingFactory.createBehandling(
        id = id,
        søknad = søknad,
        status = status,
        sakId = behandlingId,
        saksnummer = Saksnummer(0),
        fnr = FnrGenerator.random(),
        oppgaveId = OppgaveId("1234")
    )

    private fun defaultSimulering(): () -> Simulering = {
        Simulering(
            gjelderId = Fnr("12345678910"),
            gjelderNavn = "NAVN NAVN",
            datoBeregnet = LocalDate.now(),
            nettoBeløp = 54600,
            periodeList = listOf()
        )
    }
}
