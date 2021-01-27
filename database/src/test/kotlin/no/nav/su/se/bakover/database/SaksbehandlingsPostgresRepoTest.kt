package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
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

    @Nested
    inner class Opprettet {
        @Test
        fun `kan sette inn tom saksbehandling`() {
            withMigratedDb {
                val sak = setup()
                val søknad = sak.søknader().first()

                val saksbehandling = Saksbehandling.Søknadsbehandling.Opprettet(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                    fnr = sak.fnr
                )

                repo.lagre(saksbehandling)
                repo.hent(saksbehandlingId) shouldBe saksbehandling
            }
        }

        @Test
        fun `kan oppdatere tom saksbehandling med ny behandlingsinformasjon`() {
            withMigratedDb {
                val sak = setup()
                val søknad = sak.søknader().first()
                val saksbehandling = Saksbehandling.Søknadsbehandling.Opprettet(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                    fnr = sak.fnr
                )

                repo.lagre(saksbehandling)
                repo.hent(saksbehandlingId) shouldBe saksbehandling

                val vilkårsvurdert = Saksbehandling.Søknadsbehandling.Vilkårsvurdert.Innvilget(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                    fnr = sak.fnr
                )

                repo.lagre(vilkårsvurdert)
                repo.hent(saksbehandlingId) shouldBe vilkårsvurdert
            }
        }

        @Test
        fun `oppdaterer status og behandlingsinformasjon og sletter beregning og simulering hvis de eksisterer`() {
            withMigratedDb {
                val sak = setup()
                val søknad = sak.søknader().first()
                val saksbehandling = Saksbehandling.Søknadsbehandling.Simulert(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                    fnr = sak.fnr,
                    beregning = TestBeregning.toSnapshot(),
                    simulering = Simulering(
                        gjelderId = fnr,
                        gjelderNavn = "navn",
                        datoBeregnet = LocalDate.EPOCH,
                        nettoBeløp = 100,
                        periodeList = emptyList()
                    )
                )

                repo.lagre(saksbehandling)
                repo.hent(saksbehandling.id) shouldBe saksbehandling

                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldNotBe null
                    }
                }

                val vilkårsvurdert = Saksbehandling.Søknadsbehandling.Vilkårsvurdert.Innvilget(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                    fnr = sak.fnr
                )

                repo.lagre(vilkårsvurdert)
                repo.hent(vilkårsvurdert.id) shouldBe vilkårsvurdert

                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldBe null
                        it.stringOrNull("simulering") shouldBe null
                    }
                }
            }
        }
    }

    private fun setup() = testDataHelper.nySakMedJournalførtSøknadOgOppgave(
        fnr = fnr,
        oppgaveId = oppgaveId,
        journalpostId = journalpostId
    )
}
