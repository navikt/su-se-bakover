package no.nav.su.se.bakover.domain

import io.kotest.assertions.arrow.either.shouldBeLeftOfType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.BEREGNET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.OPPRETTET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.SIMULERT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withVilkårIkkeVurdert
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class BehandlingTest {

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()
    private val aktørId = AktørId("aktørId")
    private val søknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())

    companion object {
        val oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = UUID.randomUUID(),
            utbetalinger = mutableListOf()
        )
    }

    private lateinit var behandling: Behandling

    @BeforeEach
    fun beforeEach() {
        behandling = createBehandling(id1, OPPRETTET)
    }

    @Test
    fun equals() {
        val a = createBehandling(id1, status = VILKÅRSVURDERT_INNVILGET)
        val b = Behandling(
            id1,
            søknad = søknad,
            status = VILKÅRSVURDERT_INNVILGET,
            sakId = id1
        )
        val c = createBehandling(id2, status = VILKÅRSVURDERT_INNVILGET)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
        assertNotEquals(a, null)
        assertNotEquals(a, Object())
    }

    @Test
    fun hashcode() {
        val a = createBehandling(id1, status = VILKÅRSVURDERT_INNVILGET)
        val b = Behandling(
            id1,
            søknad = søknad,
            status = VILKÅRSVURDERT_INNVILGET,
            sakId = id1
        )
        val c = createBehandling(id2, status = VILKÅRSVURDERT_INNVILGET)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a.hashCode(), c.hashCode())
        val hashSet = hashSetOf(a, b, c)
        assertEquals(2, hashSet.size)
        assertTrue(hashSet.contains(a))
        assertTrue(hashSet.contains(c))
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
                        status = Behandlingsinformasjon.Formue.Status.Ok,
                        verdiIkkePrimærbolig = 52889,
                        verdiKjøretøy = 8823,
                        innskudd = 3291,
                        verdipapir = 291,
                        pengerSkyldt = 8921,
                        kontanter = 49,
                        depositumskonto = 315177,
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
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> { opprettet.opprettBeregning(1.januar(2020), 31.desember(2020)) }
                .also {
                    it.msg shouldContain "Illegal operation"
                    it.msg shouldContain "opprettBeregning"
                    it.msg shouldContain "state: OPPRETTET"
                }
            assertThrows<Behandling.TilstandException> { opprettet.simuler(defaultUtbetaling()) }
            assertThrows<Behandling.TilstandException> {
                opprettet.sendTilAttestering(
                    aktørId,
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
            vilkårsvurdert.status() shouldBe BEREGNET
        }

        @Test
        fun `should create beregning`() {
            val fom = 1.januar(2020)
            val tom = 31.desember(2020)
            vilkårsvurdert.opprettBeregning(
                fom = fom,
                tom = tom
            )
            val beregning = vilkårsvurdert.beregning()!!
            beregning.fom shouldBe fom
            beregning.tom shouldBe tom
            beregning.sats shouldBe Sats.HØY
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
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.simuler(defaultUtbetaling()) }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.sendTilAttestering(
                    aktørId,
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
            vilkårsvurdert.sendTilAttestering(aktørId, saksbehandler)
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
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.simuler(defaultUtbetaling()) }
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
            beregnet.status() shouldBe BEREGNET
        }

        @Test
        fun `skal kunne simuleres`() {
            beregnet.simuler(defaultUtbetaling())
            beregnet.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            beregnet.opprettBeregning(1.januar(2020), 31.desember(2020))
            beregnet.status() shouldBe BEREGNET
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
                    aktørId,
                    Saksbehandler("S123456")
                )
            }
            assertThrows<Behandling.TilstandException> {
                beregnet.iverksett(
                    Attestant("A123456")
                )
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
            simulert.simuler(defaultUtbetaling())
            simulert.status() shouldBe SIMULERT
        }

        @Test
        fun `skal kunne sende til attestering`() {
            val saksbehandler = Saksbehandler("S123456")
            simulert.sendTilAttestering(aktørId, saksbehandler)

            simulert.status() shouldBe TIL_ATTESTERING_INNVILGET
            simulert.saksbehandler() shouldBe saksbehandler
        }

        @Test
        fun `skal kunne beregne på nytt`() {
            simulert.opprettBeregning(1.januar(2020), 31.desember(2020))
            simulert.status() shouldBe BEREGNET
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
            simulert.simuler(defaultUtbetaling())
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
                avslått.simuler(defaultUtbetaling())
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
            tilAttestering.simuler(defaultUtbetaling())
            tilAttestering.sendTilAttestering(AktørId(id1.toString()), Saksbehandler("S123456"))

            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456"))
                .shouldBeLeftOfType<Behandling.KunneIkkeUnderkjenne>()

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
                tilAttestering.simuler(defaultUtbetaling())
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(AktørId(id1.toString()), Saksbehandler("S123456"))
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
            tilAttestering.sendTilAttestering(AktørId(id1.toString()), Saksbehandler("S123456"))
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"))
                .shouldBeLeftOfType<Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456"))
                .shouldBeLeftOfType<Behandling.KunneIkkeUnderkjenne>()

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
                tilAttestering.simuler(defaultUtbetaling())
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(AktørId(id1.toString()), Saksbehandler("S123456"))
            }
        }
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ) = Behandling(
        id = id,
        søknad = søknad,
        status = status,
        sakId = id1
    )

    private fun defaultUtbetaling() = Utbetaling(fnr = Fnr("12345678910"), utbetalingslinjer = emptyList())
}
