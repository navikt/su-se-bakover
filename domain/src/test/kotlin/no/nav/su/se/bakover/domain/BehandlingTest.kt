package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.either.shouldBeLeftOfType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.BEREGNET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
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
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
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
    private lateinit var observer: DummyObserver

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
            observer.oppdatertBehandlingsinformasjon shouldBe expected
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

            opprettet.oppdaterBehandlingsinformasjon(expected)

            observer.oppdatertBehandlingsinformasjon shouldBe expected
            observer.oppdatertBehandlingsinformasjon.let {
                it.fastOppholdINorge shouldNotBe null
                it.flyktning shouldNotBe null
                it.formue shouldNotBe null
                it.lovligOpphold shouldNotBe null
                it.oppholdIUtlandet shouldNotBe null
                it.bosituasjon shouldNotBe null
                it.uførhet shouldNotBe null
            }
        }

        @Test
        fun `transition to Vilkårsvurdert`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withAlleVilkårOppfylt())
            opprettet.status() shouldBe VILKÅRSVURDERT_INNVILGET
            observer.oppdatertStatus shouldBe opprettet.status()
        }

        @Test
        fun `transition to Avslått`() {
            opprettet.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(opprettet).withVilkårAvslått())
            opprettet.status() shouldBe VILKÅRSVURDERT_AVSLAG
            observer.oppdatertStatus shouldBe opprettet.status()
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
            assertThrows<Behandling.TilstandException> { opprettet.simuler(SimuleringClientStub) }
            assertThrows<Behandling.TilstandException> { opprettet.sendTilAttestering(aktørId, OppgaveClientStub, Saksbehandler("S123456")) }
            assertThrows<Behandling.TilstandException> {
                opprettet.iverksett(
                    Attestant("A123456"),
                    UtbetalingPublisherStub
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
            observer.oppdatertStatus shouldBe vilkårsvurdert.status()
        }

        @Test
        fun `legal operations`() {
            vilkårsvurdert.opprettBeregning(1.januar(2020), 31.desember(2020))
            vilkårsvurdert.status() shouldBe BEREGNET
        }

        @Test
        fun `should create beregning`() {
            val fraOgMed = 1.januar(2020)
            val tilOgMed = 31.desember(2020)
            vilkårsvurdert.opprettBeregning(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed
            )
            observer.opprettetBeregning.first shouldBe id1
            observer.opprettetBeregning.second shouldNotBe null
            val beregning = observer.opprettetBeregning.second
            beregning.fraOgMed shouldBe fraOgMed
            beregning.tilOgMed shouldBe tilOgMed
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
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.simuler(SimuleringClientStub) }
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.sendTilAttestering(aktørId, OppgaveClientStub, Saksbehandler("S123456")) }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.iverksett(
                    Attestant("A123456"),
                    UtbetalingPublisherStub
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
            observer.oppdatertStatus shouldBe vilkårsvurdert.status()
        }

        @Test
        fun `skal kunne sende til attestering`() {
            val saksbehandler = Saksbehandler("S123456")
            vilkårsvurdert.sendTilAttestering(aktørId, OppgaveClientStub, saksbehandler)
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
            assertThrows<Behandling.TilstandException> { vilkårsvurdert.simuler(SimuleringClientStub) }
            assertThrows<Behandling.TilstandException> {
                vilkårsvurdert.iverksett(Attestant("A123456"), UtbetalingPublisherStub)
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
            observer.oppdatertStatus shouldBe beregnet.status()
        }

        @Test
        fun `skal kunne simuleres`() {
            beregnet.simuler(SimuleringClientStub)
            beregnet.status() shouldBe SIMULERT
        }

        @Test
        fun `sletter eksisterende utbetalinger ved ny simulering`() {
            beregnet.simuler(SimuleringClientStub)
            val utbetaling = beregnet.utbetaling()
            beregnet.simuler(SimuleringClientStub)
            val nyUtbetaling = beregnet.utbetaling()
            utbetaling shouldNotBe nyUtbetaling
            observer.slettetUtbetaling shouldBe utbetaling
        }

        @Test
        fun `tillater ikke sletting av oversendte eller kvitterte utbetalinger`() {
            beregnet.simuler(SimuleringClientStub)
            beregnet.utbetaling()!!.apply {
                addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK, ""))
            }
            assertThrows<IllegalStateException> { beregnet.simuler(SimuleringClientStub) }
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
            assertThrows<Behandling.TilstandException> { beregnet.sendTilAttestering(aktørId, OppgaveClientStub, Saksbehandler("S123456")) }
            assertThrows<Behandling.TilstandException> {
                beregnet.iverksett(
                    Attestant("A123456"),
                    UtbetalingPublisherStub
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
            simulert.simuler(SimuleringClientStub)
            simulert.status() shouldBe SIMULERT
            observer.oppdatertStatus shouldBe simulert.status()
        }

        @Test
        fun `skal kunne sende til attestering`() {
            val saksbehandler = Saksbehandler("S123456")
            simulert.sendTilAttestering(aktørId, OppgaveClientStub, saksbehandler)

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
            simulert.simuler(SimuleringClientStub)
            simulert.status() shouldBe SIMULERT
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                simulert.iverksett(
                    Attestant("A123456"),
                    UtbetalingPublisherStub
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
            observer.oppdatertStatus shouldBe avslått.status()
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
                avslått.simuler(SimuleringClientStub)
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
            tilAttestering.simuler(SimuleringClientStub)
            tilAttestering.sendTilAttestering(AktørId(id1.toString()), OppgaveClientStub, Saksbehandler("S123456"))

            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
            observer.oppdatertStatus shouldBe tilAttestering.status()
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"), UtbetalingPublisherStub).shouldBeLeftOfType<Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456")).shouldBeLeftOfType<Behandling.KunneIkkeUnderkjenne>()

            tilAttestering.attestant() shouldBe null
            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
        }

        @Test
        fun `skal kunne iverksette`() {
            tilAttestering.iverksett(Attestant("A123456"), UtbetalingPublisherStub)
            tilAttestering.status() shouldBe IVERKSATT_INNVILGET
            tilAttestering.utbetaling()!!.getOppdragsmelding() shouldBe Oppdragsmelding(
                SENDT,
                "great success",
                UtbetalingPublisherStub.tidspunkt
            )
            observer.oppdatertStatus shouldBe tilAttestering.status()
        }

        @Test
        fun `oversendelse av av utbetaling feiler`() {
            val tidspunkt = now()
            tilAttestering.iverksett(
                Attestant("A123456"),
                object : UtbetalingPublisher {
                    override fun publish(
                        nyUtbetaling: NyUtbetaling
                    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Oppdragsmelding> =
                        UtbetalingPublisher.KunneIkkeSendeUtbetaling(
                            Oppdragsmelding(
                                Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                                "some xml here",
                                tidspunkt
                            )
                        ).left()
                }
            )
            tilAttestering.status() shouldBe TIL_ATTESTERING_INNVILGET
            tilAttestering.utbetaling()!!.getOppdragsmelding() shouldBe Oppdragsmelding(
                FEIL,
                "some xml here",
                tidspunkt
            )
        }

        @Test
        fun `legger til kvittering for utbetaling`() {
            tilAttestering.iverksett(Attestant("A123456"), UtbetalingPublisherStub)
            val utbetaling = tilAttestering.utbetaling()!!
            utbetaling.getKvittering() shouldBe null
            val kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "someXmlHere"
            )
            utbetaling.addKvittering(kvittering)
            utbetaling.getKvittering() shouldBe kvittering
        }

        @Test
        fun `ignorer kvittering for utbetaling hvis den finnes fra før`() {
            tilAttestering.iverksett(Attestant("A123456"), UtbetalingPublisherStub)
            val utbetaling = tilAttestering.utbetaling()!!
            utbetaling.getKvittering() shouldBe null
            val kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "someXmlHere"
            )
            utbetaling.addKvittering(kvittering)
            utbetaling.getKvittering() shouldBe kvittering
            utbetaling.addKvittering(kvittering.copy(mottattTidspunkt = kvittering.mottattTidspunkt.plusSeconds(1)))
            utbetaling.getKvittering() shouldBe kvittering
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                tilAttestering.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.simuler(SimuleringClientStub)
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(AktørId(id1.toString()), OppgaveClientStub, Saksbehandler("S123456"))
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
            tilAttestering.sendTilAttestering(AktørId(id1.toString()), OppgaveClientStub, Saksbehandler("S123456"))
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
            observer.oppdatertStatus shouldBe tilAttestering.status()
        }

        @Test
        fun `skal ikke kunne attestera sin egen saksbehandling`() {
            tilAttestering.iverksett(Attestant("S123456"), UtbetalingPublisherStub).shouldBeLeftOfType<Behandling.IverksettFeil.AttestantOgSaksbehandlerErLik>()
            tilAttestering.underkjenn("Detta skal ikke gå.", Attestant("S123456")).shouldBeLeftOfType<Behandling.KunneIkkeUnderkjenne>()

            tilAttestering.attestant() shouldBe null
            tilAttestering.status() shouldBe TIL_ATTESTERING_AVSLAG
        }

        @Test
        fun `skal kunne iverksette`() {
            tilAttestering.iverksett(Attestant("A123456"), UtbetalingPublisherStub)
            tilAttestering.status() shouldBe IVERKSATT_AVSLAG
            observer.oppdatertStatus shouldBe tilAttestering.status()
        }

        @Test
        fun `illegal operations`() {
            assertThrows<Behandling.TilstandException> {
                tilAttestering.opprettBeregning(1.januar(2020), 31.desember(2020))
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.simuler(SimuleringClientStub)
            }
            assertThrows<Behandling.TilstandException> {
                tilAttestering.sendTilAttestering(AktørId(id1.toString()), OppgaveClientStub, Saksbehandler("S123456"))
            }
        }
    }

    private class DummyObserver :
        BehandlingPersistenceObserver,
        Oppdrag.OppdragPersistenceObserver,
        UtbetalingPersistenceObserver {
        lateinit var opprettetBeregning: Pair<UUID, Beregning>
        lateinit var slettetBeregningBehandlingId: UUID
        lateinit var oppdatertStatus: BehandlingsStatus
        lateinit var oppdragsmelding: Oppdragsmelding
        lateinit var oppdatertBehandlingsinformasjon: Behandlingsinformasjon
        lateinit var slettetUtbetaling: Utbetaling

        override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
            opprettetBeregning = behandlingId to beregning
            return opprettetBeregning.second
        }

        override fun deleteBeregninger(behandlingId: UUID) {
            slettetBeregningBehandlingId = behandlingId
        }

        override fun oppdaterBehandlingStatus(
            behandlingId: UUID,
            status: BehandlingsStatus
        ): BehandlingsStatus {
            oppdatertStatus = status
            return status
        }

        override fun oppdaterBehandlingsinformasjon(
            behandlingId: UUID,
            behandlingsinformasjon: Behandlingsinformasjon
        ): Behandlingsinformasjon {
            oppdatertBehandlingsinformasjon = behandlingsinformasjon
            return behandlingsinformasjon
        }

        override fun hentOppdrag(sakId: UUID): Oppdrag {
            return oppdrag.copy(sakId = sakId).also { it.addObserver(this) }
        }

        override fun hentFnr(sakId: UUID): Fnr {
            return Fnr("12345678910")
        }

        override fun settSaksbehandler(behandlingId: UUID, saksbehandler: Saksbehandler): Saksbehandler {
            return saksbehandler
        }

        override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
            return attestant
        }

        override fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30) {
        }

        override fun opprettUtbetaling(oppdragId: UUID30, utbetaling: Utbetaling) {
            utbetaling.addObserver(this)
        }

        override fun slettUtbetaling(utbetaling: Utbetaling) {
            slettetUtbetaling = utbetaling
        }

        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering) {
        }

        override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
            return kvittering
        }

        override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
            this.oppdragsmelding = oppdragsmelding
            return this.oppdragsmelding
        }
    }

    object OppgaveClientStub : OppgaveClient {
        override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, Long> {
            return Either.right(1L)
        }
    }

    object SimuleringClientStub : SimuleringClient {
        override fun simulerUtbetaling(
            nyUtbetaling: NyUtbetaling
        ): Either<SimuleringFeilet, Simulering> {
            return Either.right(
                Simulering(
                    gjelderId = Fnr("12345678910"),
                    gjelderNavn = "gjelderNavn",
                    datoBeregnet = 1.mai(2020),
                    nettoBeløp = 15000,
                    emptyList()
                )
            )
        }
    }

    object UtbetalingPublisherStub : UtbetalingPublisher {
        val tidspunkt = now()
        override fun publish(
            nyUtbetaling: NyUtbetaling
        ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Oppdragsmelding> = Oppdragsmelding(
            status = SENDT,
            originalMelding = "great success",
            tidspunkt = tidspunkt
        ).right()
    }

    private fun createBehandling(
        id: UUID,
        status: BehandlingsStatus
    ) = Behandling(
        id = id,
        søknad = søknad,
        status = status,
        sakId = id1
    ).also {
        observer = DummyObserver()
        it.addObserver(observer)
    }
}
