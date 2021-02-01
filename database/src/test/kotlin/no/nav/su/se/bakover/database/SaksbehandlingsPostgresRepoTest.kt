package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
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

    private val beregning = TestBeregning.toSnapshot()
    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = LocalDate.EPOCH,
        nettoBeløp = 100,
        periodeList = emptyList()
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb {
            val vilkårsvurdert = uavklartVilkårsvurdering()
            repo.lagre(vilkårsvurdert)
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb {
            val vilkårsvurdert = uavklartVilkårsvurdering().tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                    .withAlleVilkårOppfylt()
            )
            repo.lagre(vilkårsvurdert)
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb {
            val vilkårsvurdert = uavklartVilkårsvurdering().tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                    .withVilkårAvslått()
            )
            repo.lagre(vilkårsvurdert)
            repo.hent(saksbehandlingId).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `oppdaterer status og behandlingsinformasjon og sletter beregning og simulering hvis de eksisterer`() {

        withMigratedDb {
            val saksbehandling = uavklartVilkårsvurdering().also {
                repo.lagre(it)
                repo.hent(it.id) shouldBe it
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
            val beregnet = saksbehandling.tilBeregnet(
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

    @Test
    fun `til attestering`() {
        withMigratedDb {
            val nyOppgaveId = OppgaveId("tilAttesteringOppgaveId")
            val vilkårsvurdert = uavklartVilkårsvurdering().tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
            ).also {
                repo.lagre(it)
            }
            vilkårsvurdert.tilBeregnet(beregning).also {
                repo.lagre(it)
            }.tilSimulert(simulering).also {
                repo.lagre(it)
            }.tilAttestering(saksbehandler).nyOppgaveId(nyOppgaveId).also {
                repo.lagre(it)
                repo.hent(saksbehandlingId).also {
                    it shouldBe Søknadsbehandling.TilAttestering.Innvilget(
                        id = vilkårsvurdert.id,
                        opprettet = vilkårsvurdert.opprettet,
                        sakId = vilkårsvurdert.sakId,
                        saksnummer = vilkårsvurdert.saksnummer,
                        søknad = vilkårsvurdert.søknad,
                        oppgaveId = nyOppgaveId,
                        behandlingsinformasjon = vilkårsvurdert.behandlingsinformasjon,
                        fnr = vilkårsvurdert.fnr,
                        beregning = beregning,
                        simulering = simulering,
                        saksbehandler = saksbehandler,
                    )
                }
            }
        }
    }

    private fun uavklartVilkårsvurdering(
        sak: Sak = setup(),
        søknad: Søknad.Journalført.MedOppgave = sak.søknader().first() as Søknad.Journalført.MedOppgave
    ) = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = saksbehandlingId,
        opprettet = opprettet,
        sakId = sak.id,
        saksnummer = sak.saksnummer,
        søknad = søknad,
        oppgaveId = oppgaveId,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = sak.fnr
    )

    private fun setup() = testDataHelper.nySakMedJournalførtSøknadOgOppgave(
        fnr = fnr,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId
    )
}
