package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.journalførtIverksettingForAvslag
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import org.junit.jupiter.api.Test

internal class VedtakPosgresRepoTest {
    private val datasource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(datasource)
    private val vedtakRepo = testDataHelper.vedtakRepo

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling)

            vedtakRepo.lagre(vedtak)

            vedtakRepo.hent(vedtak.id) shouldBe vedtak
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning(journalførtIverksettingForAvslag)
            val vedtak = Vedtak.AvslåttStønad.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

            vedtakRepo.lagre(vedtak)

            vedtakRepo.hent(vedtak.id) shouldBe vedtak
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for søknadsbehandling`() {
        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling)

            vedtakRepo.lagre(vedtak)

            datasource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe søknadsbehandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom revurdering og vedtak ved lagring av vedtak for revurdering`() {
        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val søknadsbehandlingVedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling)

            vedtakRepo.lagre(søknadsbehandlingVedtak)

            val nyRevurdering = testDataHelper.nyRevurdering(søknadsbehandlingVedtak)
            val iverksattRevurdering = IverksattRevurdering(
                id = nyRevurdering.id,
                opprettet = nyRevurdering.opprettet,
                periode = søknadsbehandlingVedtak.periode,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                beregning = søknadsbehandlingVedtak.beregning,
                simulering = søknadsbehandlingVedtak.simulering,
                oppgaveId = OppgaveId(""),
                attestering = Attestering.Iverksatt(søknadsbehandlingVedtak.attestant),
                utbetalingId = søknadsbehandlingVedtak.utbetalingId,
                eksterneIverksettingsteg = søknadsbehandlingVedtak.journalføringOgBrevdistribusjon
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = Vedtak.InnvilgetStønad.fromRevurdering(iverksattRevurdering)

            vedtakRepo.lagre(revurderingVedtak)

            datasource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to revurderingVedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe null
                        it.stringOrNull("revurderingId") shouldBe iverksattRevurdering.id.toString()
                    }
            }
        }
    }

    @Test
    fun `hent alle aktive vedtak`() {
        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val vedtakSomErAktivt = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling).copy(periode = Periode.create(1.februar(2021), 31.mars(2021)))
            val vedtakUtenforAktivPeriode = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling).copy(periode = Periode.create(1.januar(2021), 31.januar(2021)))
            vedtakRepo.lagre(vedtakSomErAktivt)
            vedtakRepo.lagre(vedtakUtenforAktivPeriode)

            val actual = vedtakRepo.hentAktive(1.februar(2021))
            actual.first() shouldBe vedtakSomErAktivt
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning(journalførtIverksettingForAvslag)
            val vedtak = Vedtak.AvslåttStønad.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

            vedtakRepo.lagre(vedtak)

            datasource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe søknadsbehandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppdaterer vedtak med journalpost og brevbestilling`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning(JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert)
            val vedtak = Vedtak.AvslåttStønad.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

            vedtakRepo.lagre(vedtak)
            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi"))
                    )
                )
            )
            vedtakRepo.hent(vedtak.id)!! shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    journalpostId = JournalpostId("jp"),
                    brevbestillingId = BrevbestillingId(("bi"))
                )
            )
        }

        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling)

            vedtakRepo.lagre(vedtak)
            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi"))
                    )
                )
            )
            vedtakRepo.hent(vedtak.id)!! shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    journalpostId = JournalpostId("jp"),
                    brevbestillingId = BrevbestillingId(("bi"))
                )
            )
        }
    }

    @Test
    fun `kobler ikke den samme behandlingen og vedtaket flere ganger ved oppdatering av vedtak`() {
        withMigratedDb {
            val (søknadsbehandling, _) = testDataHelper.nyIverksattInnvilget()
            val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(søknadsbehandling)

            vedtakRepo.lagre(vedtak)
            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi"))
                    )
                )
            )

            datasource.withSession { session ->
                """
                        SELECT count(*) from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.int("count") shouldBe 1
                    }
            }
        }
    }
}
