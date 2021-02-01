package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
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
                val søknad = sak.søknader().first() as Søknad.Journalført.MedOppgave

                val saksbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
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
                val søknad = sak.søknader().first() as Søknad.Journalført.MedOppgave
                val saksbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                    fnr = sak.fnr
                )

                repo.lagre(saksbehandling)
                repo.hent(saksbehandlingId) shouldBe saksbehandling

                val vilkårsvurdert = Søknadsbehandling.Vilkårsvurdert.Innvilget(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
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
                val søknad = sak.søknader().first() as Søknad.Journalført.MedOppgave
                val saksbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                    fnr = sak.fnr
                )
                repo.lagre(saksbehandling)
                val simulertSaksbehandling = saksbehandling.tilBeregnet(
                    beregning = TestBeregning.toSnapshot()
                ).tilSimulert(
                    Simulering(
                        gjelderId = fnr,
                        gjelderNavn = "navn",
                        datoBeregnet = LocalDate.EPOCH,
                        nettoBeløp = 100,
                        periodeList = emptyList()
                    )
                )

                repo.lagre(simulertSaksbehandling)
                repo.hent(simulertSaksbehandling.id) shouldBe simulertSaksbehandling

                dataSource.withSession {
                    "select * from behandling where id = :id".hent(mapOf("id" to saksbehandlingId), it) {
                        it.stringOrNull("beregning") shouldNotBe null
                        it.stringOrNull("simulering") shouldNotBe null
                    }
                }

                val vilkårsvurdert = Søknadsbehandling.Vilkårsvurdert.Innvilget(
                    id = saksbehandlingId,
                    opprettet = opprettet,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
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
