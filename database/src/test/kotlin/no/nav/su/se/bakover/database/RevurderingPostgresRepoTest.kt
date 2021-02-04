package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.util.UUID

internal class RevurderingPostgresRepoTest {
    private val ds = EmbeddedDatabase.instance()
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(ds)
    private val repo: RevurderingPostgresRepo = RevurderingPostgresRepo(ds, søknadsbehandlingRepo)
    private val søknadRepo = SøknadPostgresRepo(ds)
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val saksbehandler = Saksbehandler("Sak S. Behandler")
    private val periode = Periode.create(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020)
    )

    @Test
    fun `kan lagre og hente en revurdering`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet
        }
    }

    @Test
    fun `kan ikke overskrive en opprettet revurdering med en ny opprettet revurdering`() {
        withMigratedDb {
            assertThrows<PSQLException> {
                val behandling = setupIverksattBehandling()

                val original = OpprettetRevurdering(
                    id = UUID.randomUUID(),
                    periode = periode,
                    opprettet = Tidspunkt.now(),
                    tilRevurdering = behandling,
                    saksbehandler = saksbehandler
                )

                repo.lagre(original)

                val kopiMedNyttTidspunkt = original.copy(
                    opprettet = Tidspunkt.now()
                )
                repo.lagre(kopiMedNyttTidspunkt)
            }
        }
    }

    @Test
    fun `kan kan overskrive en opprettet med beregnet`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnetRevurdering = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnetRevurdering)
            assert(repo.hent(opprettet.id) is BeregnetRevurdering)
        }
    }

    @Test
    fun `beregnet kan overskrives med ny beregnet`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnet)

            val nyBeregnet = BeregnetRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = Saksbehandler("ny saksbehandler"),
                beregning = beregnet.beregning
            )

            repo.lagre(nyBeregnet)

            val hentet = repo.hent(opprettet.id)

            hentet shouldNotBe opprettet
            hentet shouldNotBe beregnet
            hentet!!.saksbehandler shouldBe nyBeregnet.saksbehandler
        }
    }

    @Test
    fun `kan overskrive en beregnet med simulert`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf()
                )
            )

            repo.lagre(simulert)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is SimulertRevurdering)
        }
    }

    @Test
    fun `kan overskrive en simulert med en beregnet`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf()
                )
            )

            repo.lagre(simulert)
            repo.lagre(beregnet)
            val hentet = repo.hent(opprettet.id)

            assert(hentet is BeregnetRevurdering)
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf()
                )
            )

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(oppgaveId = OppgaveId("oppgaveId"), saksbehandler = saksbehandler)

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering)
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb {
            val behandling = setupIverksattBehandling()
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.now(),
                tilRevurdering = behandling,
                saksbehandler = saksbehandler
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = behandling,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf()
                )
            )

            repo.lagre(simulert)

            val tilAttestering = simulert.tilAttestering(
                oppgaveId = OppgaveId("oppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler")
            )

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering)
            hentet!!.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    private fun setupIverksattBehandling(): Søknadsbehandling.Iverksatt.Innvilget {
        val sak = testDataHelper.insertSak(FnrGenerator.random())
        val søknad: Søknad.Journalført.MedOppgave = testDataHelper.insertSøknad(sak.id).let {
            søknadRepo.oppdaterOppgaveId(it.id, OppgaveId(""))
            søknadRepo.oppdaterjournalpostId(it.id, JournalpostId(""))
            søknadRepo.hentSøknad(it.id)
        }.let {
            søknadRepo.hentSøknad(it!!.id)
        } as Søknad.Journalført.MedOppgave

        val simulering = Simulering(
            gjelderId = sak.fnr,
            gjelderNavn = "",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf()
        )
        val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sak.id,
            saksnummer = Saksnummer(-1),
            fnr = sak.fnr,
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Attestant("attestant"),
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = simulering,
            utbetalingsrequest = Utbetalingsrequest(""),
        )

        testDataHelper.opprettUtbetaling(utbetaling)

        val vilkårsvurdert = Søknadsbehandling.Vilkårsvurdert.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sak.id,
            saksnummer = Saksnummer(-1),
            søknad = søknad,
            oppgaveId = OppgaveId(value = ""),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            fnr = sak.fnr,
        )

        søknadsbehandlingRepo.lagre(vilkårsvurdert)
        vilkårsvurdert.tilBeregnet(TestBeregning).let {
            søknadsbehandlingRepo.lagre(it)
            it.tilSimulert(simulering).let {
                søknadsbehandlingRepo.lagre(it)
                it.tilAttestering(saksbehandler).let {
                    søknadsbehandlingRepo.lagre(it)
                    it.tilIverksatt(Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")), utbetaling.id).let {
                        søknadsbehandlingRepo.lagre(it)
                    }
                }
            }
        }

        return søknadsbehandlingRepo.hent(vilkårsvurdert.id) as Søknadsbehandling.Iverksatt.Innvilget
    }
}
