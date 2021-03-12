package no.nav.su.se.bakover.database

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class RevurderingPostgresRepoTest {
    private val ds = EmbeddedDatabase.instance()
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(ds)
    private val repo: RevurderingPostgresRepo = RevurderingPostgresRepo(ds, søknadsbehandlingRepo)
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val saksbehandler = Saksbehandler("Sak S. Behandler")
    private val periode = Periode.create(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020)
    )

    @Test
    fun `kan lagre og hente en revurdering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet
        }
    }

    @Test
    fun `kan oppdatere revurderingsperiode`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)

            val opprettetRevurdering = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )
            repo.lagre(opprettetRevurdering)
            val beregnetRevurdering = BeregnetRevurdering.Innvilget(
                id = opprettetRevurdering.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning.toSnapshot(),
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnetRevurdering)
            repo.hent(beregnetRevurdering.id) shouldBe beregnetRevurdering

            val revurderingMedNyPeriode = OpprettetRevurdering(
                id = beregnetRevurdering.id,
                periode = Periode.create(1.juni(2020), 30.juni(2020)),
                opprettet = Tidspunkt.now(),
                tilRevurdering = beregnetRevurdering.tilRevurdering,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(revurderingMedNyPeriode)
            val oppdatertRevurdering = repo.hent(beregnetRevurdering.id)
            assert(oppdatertRevurdering is OpprettetRevurdering)
            oppdatertRevurdering!!.periode shouldBe revurderingMedNyPeriode.periode
        }
    }

    @Test
    fun `kan kan overskrive en opprettet med beregnet`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnetRevurdering = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnetRevurdering)
            assert(repo.hent(opprettet.id) is BeregnetRevurdering.Innvilget)
        }
    }

    @Test
    fun `beregnet kan overskrives med ny beregnet`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val nyBeregnet = BeregnetRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = Saksbehandler("ny saksbehandler"),
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid")
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
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
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
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
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

            assert(hentet is BeregnetRevurdering.Innvilget)
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
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
                simulert.tilAttestering(attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"), saksbehandler = saksbehandler)

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering)
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
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
                attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler")
            )

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering)
            hentet!!.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    @Test
    fun `kan lagre og hente en iverksatt revurdering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val attestant = NavIdentBruker.Attestant("Attestansson")

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )
            repo.lagre(opprettet)

            val tilAttestering = RevurderingTilAttestering(
                id = opprettet.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning.toSnapshot(),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "Navn Navnesson",
                    datoBeregnet = LocalDate.now(),
                    nettoBeløp = 5,
                    periodeList = listOf()
                ),
                oppgaveId = OppgaveId(value = ""),
            )
            val utbetaling = testDataHelper.nyUtbetalingUtenKvittering(
                revurderingTilAttestering = tilAttestering,
            )

            val iverksatt = tilAttestering.iverksett(
                attestant = attestant,
                utbetal = { utbetaling.id.right() },
            ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

            repo.lagre(iverksatt)
            repo.hent(iverksatt.id) shouldBe iverksatt
            repo.hentRevurderingForUtbetaling(iverksatt.utbetalingId) shouldBe iverksatt
            ds.withSession {
                repo.hentRevurderingerForSak(iverksatt.sakId, it) shouldBe listOf(iverksatt)
            }
        }
    }

    @Test
    fun `kan lagre og hente en underkjent revurdering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakForSøknadsbehandling(testDataHelper.nyOversendtUtbetalingMedKvittering().first)
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid")
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
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
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler
                )

            val attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "123"),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "feil"
            )

            repo.lagre(tilAttestering.underkjenn(attestering))

            assert(repo.hent(opprettet.id) is UnderkjentRevurdering)
            repo.hentEventuellTidligereAttestering(opprettet.id) shouldBe attestering
        }
    }
}
