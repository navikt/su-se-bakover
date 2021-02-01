package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.PersistertMånedsberegning
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class SaksbehandlingsPostgresRepoTest {

    private val dataSource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(dataSource)
    private val repo = SaksbehandlingsPostgresRepo(dataSource)

    private val saksbehandlingId = UUID.randomUUID()
    private val opprettet = Tidspunkt.now()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("oppgaveId")
    private val journalpostId = JournalpostId("journalpostId")
    private val tomBehandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
    private val behandlingsinformasjonMedAlleVilkårOppfylt =
        Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
    private val behandlingsinformasjonMedAvslag =
        Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()

    private val beregning = TestBeregning.toSnapshot()
    private val avslåttBeregning = beregning.copy(
        månedsberegninger = listOf(
            PersistertMånedsberegning(
                sumYtelse = 0,
                sumFradrag = 0.0,
                benyttetGrunnbeløp = 0,
                sats = Sats.ORDINÆR,
                satsbeløp = 0.0,
                fradrag = listOf(),
                periode = Periode.create(1.januar(2020), 31.desember(2020))
            )
        )
    )
    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = LocalDate.EPOCH,
        nettoBeløp = 100,
        periodeList = emptyList()
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val underkjentAttestering =
        Attestering.Underkjent(attestant, Attestering.Underkjent.Grunn.ANDRE_FORHOLD, "kommentar")
    private val iverksattAttestering = Attestering.Iverksatt(attestant)
    private val utbetalingId = UUID30.randomUUID()
    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")
    private val iverksattBrevbestillingId = BrevbestillingId("iverksattBrevbestillingId")

    @Test
    fun `hent tidligere attestering ved underkjenning`() {
        withMigratedDb {
            innvilgetUnderkjenning().also {
                repo.hentEventuellTidligereAttestering(saksbehandlingId).also {
                    it shouldBe underkjentAttestering
                }
            }
        }
    }

    @Test
    fun `hent for sak`() {
        withMigratedDb {
            avslåttBeregning().also {
                dataSource.withSession { session ->
                    repo.hentForSak(it.sakId, session)
                }
            }
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb {
            val vilkårsvurdert = uavklartVilkårsvurdering()
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb {
            val vilkårsvurdert = innvilgetVilkårsvurdering()
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb {
            val vilkårsvurdert = avslåttVilkårsvurdering()
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `oppdaterer status og behandlingsinformasjon og sletter beregning og simulering hvis de eksisterer`() {

        withMigratedDb {
            val uavklartVilkårsvurdering = uavklartVilkårsvurdering().also {
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
            val beregnet = uavklartVilkårsvurdering.tilBeregnet(
                beregning = beregning
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
            val simulert = beregnet.tilSimulert(
                simulering
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldNotBe null
                    }
                }
            }
            // Tilbake til vilkårsvurdert
            simulert.tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
            ).also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `til attestering innvilget`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = tilInnvilgetAttestering()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.TilAttestering.Innvilget(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = beregning,
                            simulering = simulering,
                            saksbehandler = saksbehandler,
                        )
                    }
                }
            }
        }

        @Test
        fun `til attestering avslag uten beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = tilAvslåttAttesteringUtenBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.TilAttestering.Avslag.UtenBeregning(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            saksbehandler = saksbehandler,
                        )
                    }
                }
            }
        }

        @Test
        fun `til attestering avslag med beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = tilAvslåttAttesteringMedBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = avslåttBeregning,
                            saksbehandler = saksbehandler,
                        )
                    }
                }
            }
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = innvilgetUnderkjenning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.Underkjent.Innvilget(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = beregning,
                            simulering = simulering,
                            saksbehandler = saksbehandler,
                            attestering = underkjentAttestering,
                        )
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag uten beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = underkjenningUtenBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.Underkjent.Avslag.UtenBeregning(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            saksbehandler = saksbehandler,
                            attestering = underkjentAttestering,
                        )
                    }
                }
            }
        }

        @Test
        fun `underkjent avslag med beregning`() {
            withMigratedDb {
                val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
                val tilAttestering = underkjenningMedBeregning()
                tilAttestering.nyOppgaveId(nyOppgaveId).also {
                    repo.lagre(it)
                    repo.hent(saksbehandlingId).also {
                        it shouldBe Søknadsbehandling.Underkjent.Avslag.MedBeregning(
                            id = tilAttestering.id,
                            opprettet = tilAttestering.opprettet,
                            sakId = tilAttestering.sakId,
                            saksnummer = tilAttestering.saksnummer,
                            søknad = tilAttestering.søknad,
                            oppgaveId = nyOppgaveId,
                            behandlingsinformasjon = tilAttestering.behandlingsinformasjon,
                            fnr = tilAttestering.fnr,
                            beregning = avslåttBeregning,
                            saksbehandler = saksbehandler,
                            attestering = underkjentAttestering,
                        )
                    }
                }
            }
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt avslag innvilget`() {

            withMigratedDb {
                val iverksatt = iverksattInnvilget()
                val expectedInnvilgetUtenJournalføring = Søknadsbehandling.Iverksatt.Innvilget(
                    id = iverksatt.id,
                    opprettet = iverksatt.opprettet,
                    sakId = iverksatt.sakId,
                    saksnummer = iverksatt.saksnummer,
                    søknad = iverksatt.søknad,
                    oppgaveId = iverksatt.oppgaveId,
                    behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                    fnr = iverksatt.fnr,
                    beregning = beregning,
                    simulering = simulering,
                    saksbehandler = saksbehandler,
                    attestering = iverksattAttestering,
                    eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.VenterPåKvittering,
                    utbetalingId = utbetalingId,
                )
                repo.hent(saksbehandlingId).also {
                    it shouldBe expectedInnvilgetUtenJournalføring
                }

                val journalført =
                    Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.Journalført(
                        journalpostId = iverksattJournalpostId
                    )
                repo.lagre(
                    iverksatt.copy(
                        eksterneIverksettingsteg = journalført
                    )
                ).also {
                    repo.hent(saksbehandlingId) shouldBe expectedInnvilgetUtenJournalføring.copy(
                        eksterneIverksettingsteg = journalført
                    )
                }

                val journalførtOgDistribuertBrev =
                    Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                        journalpostId = iverksattJournalpostId,
                        brevbestillingId = iverksattBrevbestillingId,
                    )
                repo.lagre(
                    iverksatt.copy(
                        eksterneIverksettingsteg = journalførtOgDistribuertBrev
                    )
                ).also {
                    repo.hent(saksbehandlingId) shouldBe expectedInnvilgetUtenJournalføring.copy(
                        eksterneIverksettingsteg = journalførtOgDistribuertBrev
                    )
                }
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {
        withMigratedDb {
            val journalført = Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.Journalført(
                journalpostId = iverksattJournalpostId,
            )
            val iverksatt = iverksattAvslagUtenBeregning(journalført)
            val expected = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = iverksatt.id,
                opprettet = iverksatt.opprettet,
                sakId = iverksatt.sakId,
                saksnummer = iverksatt.saksnummer,
                søknad = iverksatt.søknad,
                oppgaveId = iverksatt.oppgaveId,
                behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                fnr = iverksatt.fnr,
                saksbehandler = saksbehandler,
                attestering = iverksattAttestering,
                eksterneIverksettingsteg = journalført,
            )
            repo.hent(saksbehandlingId).also {

                it shouldBe expected
            }

            val journalførtOgDistribuertBrev =
                Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                    journalpostId = iverksattJournalpostId,
                    brevbestillingId = iverksattBrevbestillingId,
                )
            repo.lagre(
                iverksatt.copy(
                    eksterneIverksettingsteg = journalførtOgDistribuertBrev
                )
            ).also {
                repo.hent(saksbehandlingId) shouldBe expected.copy(
                    eksterneIverksettingsteg = journalførtOgDistribuertBrev
                )
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb {
            val eksterneIverksettingsteg =
                Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                    journalpostId = iverksattJournalpostId,
                    brevbestillingId = iverksattBrevbestillingId,
                )
            val iverksatt = iverksattAvslagMedBeregning(eksterneIverksettingsteg)
            repo.hent(saksbehandlingId).also {
                it shouldBe Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                    id = iverksatt.id,
                    opprettet = iverksatt.opprettet,
                    sakId = iverksatt.sakId,
                    saksnummer = iverksatt.saksnummer,
                    søknad = iverksatt.søknad,
                    oppgaveId = iverksatt.oppgaveId,
                    behandlingsinformasjon = iverksatt.behandlingsinformasjon,
                    fnr = iverksatt.fnr,
                    beregning = avslåttBeregning,
                    saksbehandler = saksbehandler,
                    attestering = iverksattAttestering,
                    eksterneIverksettingsteg = eksterneIverksettingsteg
                )
            }
        }
    }

    private fun uavklartVilkårsvurdering(): Søknadsbehandling.Vilkårsvurdert.Uavklart {
        val sak: Sak = setup()
        val søknad: Søknad.Journalført.MedOppgave = sak.søknader().first() as Søknad.Journalført.MedOppgave

        return Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = saksbehandlingId,
            opprettet = opprettet,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = søknad,
            oppgaveId = oppgaveId,
            behandlingsinformasjon = tomBehandlingsinformasjon,
            fnr = sak.fnr
        ).also {
            repo.lagre(it)
        }
    }

    private fun innvilgetVilkårsvurdering(): Søknadsbehandling.Vilkårsvurdert.Innvilget {
        return uavklartVilkårsvurdering().tilVilkårsvurdert(
            behandlingsinformasjonMedAlleVilkårOppfylt
        ).also {
            repo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Innvilget
    }

    private fun avslåttVilkårsvurdering(): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return uavklartVilkårsvurdering().tilVilkårsvurdert(
            behandlingsinformasjonMedAvslag
        ).also {
            repo.lagre(it)
        } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }

    private fun innvilgetBeregning(): Søknadsbehandling.Beregnet.Innvilget {
        return innvilgetVilkårsvurdering().tilBeregnet(
            beregning
        ).also {
            repo.lagre(it)
        } as Søknadsbehandling.Beregnet.Innvilget
    }

    private fun avslåttBeregning(): Søknadsbehandling.Beregnet.Avslag {
        return innvilgetVilkårsvurdering().tilBeregnet(
            avslåttBeregning
        ).also {
            repo.lagre(it)
        } as Søknadsbehandling.Beregnet.Avslag
    }

    private fun simulert(): Søknadsbehandling.Simulert {
        return innvilgetBeregning().tilSimulert(
            simulering
        ).also {
            repo.lagre(it)
        }
    }

    private fun tilInnvilgetAttestering(): Søknadsbehandling.TilAttestering.Innvilget {
        return simulert().tilAttestering(
            saksbehandler
        ).also {
            repo.lagre(it)
        }
    }

    private fun tilAvslåttAttesteringMedBeregning(): Søknadsbehandling.TilAttestering.Avslag.MedBeregning {
        return avslåttBeregning().tilAttestering(
            saksbehandler
        ).also {
            repo.lagre(it)
        }
    }

    private fun tilAvslåttAttesteringUtenBeregning(): Søknadsbehandling.TilAttestering.Avslag.UtenBeregning {
        return avslåttVilkårsvurdering().tilAttestering(
            saksbehandler
        ).also {
            repo.lagre(it)
        }
    }

    private fun innvilgetUnderkjenning(): Søknadsbehandling.Underkjent.Innvilget {
        return tilInnvilgetAttestering().tilUnderkjent(
            underkjentAttestering
        ).also {
            repo.lagre(it)
        }
    }

    private fun underkjenningUtenBeregning(): Søknadsbehandling.Underkjent.Avslag.UtenBeregning {
        return tilAvslåttAttesteringUtenBeregning().tilUnderkjent(
            underkjentAttestering
        ).also {
            repo.lagre(it)
        }
    }

    private fun underkjenningMedBeregning(): Søknadsbehandling.Underkjent.Avslag.MedBeregning {
        return tilAvslåttAttesteringMedBeregning().tilUnderkjent(
            underkjentAttestering
        ).also {
            repo.lagre(it)
        }
    }

    private fun iverksattInnvilget(): Søknadsbehandling.Iverksatt.Innvilget {
        return tilInnvilgetAttestering().tilIverksatt(
            iverksattAttestering, utbetalingId
        ).also {
            testDataHelper.opprettUtbetaling(
                Utbetaling.OversendtUtbetaling.UtenKvittering(
                    id = utbetalingId,
                    opprettet = it.opprettet,
                    sakId = it.sakId,
                    saksnummer = it.saksnummer,
                    fnr = it.fnr,
                    utbetalingslinjer = listOf(),
                    type = Utbetaling.UtbetalingsType.NY,
                    behandler = attestant,
                    avstemmingsnøkkel = Avstemmingsnøkkel(it.opprettet),
                    simulering = simulering,
                    utbetalingsrequest = Utbetalingsrequest(""),
                )
            )
            repo.lagre(it)
        }
    }

    private fun iverksattAvslagUtenBeregning(eksterneIverksettingsteg: Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg): Søknadsbehandling.Iverksatt.Avslag.UtenBeregning {
        return tilAvslåttAttesteringUtenBeregning().tilIverksatt(
            iverksattAttestering, eksterneIverksettingsteg
        ).also {
            repo.lagre(it)
        }
    }

    private fun iverksattAvslagMedBeregning(eksterneIverksettingsteg: Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg): Søknadsbehandling.Iverksatt.Avslag.MedBeregning {
        return tilAvslåttAttesteringMedBeregning().tilIverksatt(
            iverksattAttestering, eksterneIverksettingsteg
        ).also {
            repo.lagre(it)
        }
    }

    private fun setup() = testDataHelper.nySakMedJournalførtSøknadOgOppgave(
        fnr = fnr,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId
    )
}
