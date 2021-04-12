package no.nav.su.se.bakover.database.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import org.junit.jupiter.api.Test

internal class VedtakPosgresRepoTest {
    private val datasource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(datasource)
    private val vedtakRepo = testDataHelper.vedtakRepo

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            vedtakRepo.hent(vedtak.id) shouldBe vedtak
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning()
            val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

            vedtakRepo.lagre(vedtak)

            vedtakRepo.hent(vedtak.id) shouldBe vedtak
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for søknadsbehandling`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            datasource.withSession { session ->
                """
                    SELECT søknadsbehandlingId, revurderingId from behandling_vedtak where vedtakId = :vedtakId
                """.trimIndent()
                    .hent(mapOf("vedtakId" to vedtak.id), session) {
                        it.stringOrNull("søknadsbehandlingId") shouldBe vedtak.behandling.id.toString()
                        it.stringOrNull("revurderingId") shouldBe null
                    }
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom revurdering og vedtak ved lagring av vedtak for revurdering`() {
        withMigratedDb {
            val søknadsbehandlingVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val nyRevurdering = testDataHelper.nyRevurdering(søknadsbehandlingVedtak, søknadsbehandlingVedtak.periode)
            val iverksattRevurdering = IverksattRevurdering.Innvilget(
                id = nyRevurdering.id,
                periode = søknadsbehandlingVedtak.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = søknadsbehandlingVedtak.beregning,
                simulering = søknadsbehandlingVedtak.simulering,
                attestering = Attestering.Iverksatt(søknadsbehandlingVedtak.attestant),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = Vedtak.from(iverksattRevurdering, søknadsbehandlingVedtak.utbetalingId)

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
            val (søknadsbehandling, utbetaling) = testDataHelper.nyIverksattInnvilget()
            val vedtakSomErAktivt = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id)
                .copy(periode = Periode.create(1.februar(2021), 31.mars(2021)))
            val vedtakUtenforAktivPeriode = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetaling.id)
                .copy(periode = Periode.create(1.januar(2021), 31.januar(2021)))
            vedtakRepo.lagre(vedtakSomErAktivt)
            vedtakRepo.lagre(vedtakUtenforAktivPeriode)

            val actual = vedtakRepo.hentAktive(1.februar(2021))
            actual.first() shouldBe vedtakSomErAktivt
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning()
            val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

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
            val søknadsbehandling = testDataHelper.nyIverksattAvslagMedBeregning()
            val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

            vedtakRepo.lagre(vedtak)
            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi")),
                    ),
                ),
            )
            vedtakRepo.hent(vedtak.id)!! shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    journalpostId = JournalpostId("jp"),
                    brevbestillingId = BrevbestillingId(("bi")),
                ),
            )
        }

        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi")),
                    ),
                ),
            )
            vedtakRepo.hent(vedtak.id)!! shouldBe vedtak.copy(
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                    journalpostId = JournalpostId("jp"),
                    brevbestillingId = BrevbestillingId(("bi")),
                ),
            )
        }
    }

    @Test
    fun `kobler ikke den samme behandlingen og vedtaket flere ganger ved oppdatering av vedtak`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            vedtakRepo.lagre(
                vedtak.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                        journalpostId = JournalpostId("jp"),
                        brevbestillingId = BrevbestillingId(("bi")),
                    ),
                ),
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

    @Test
    fun `kan lagre et vedtak som ikke fører til endring i utbetaling`() {
        withMigratedDb {
            val søknadsbehandlingVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val nyRevurdering = testDataHelper.nyRevurdering(søknadsbehandlingVedtak, søknadsbehandlingVedtak.periode)
            val atteststertRevurdering = RevurderingTilAttestering.IngenEndring(
                id = nyRevurdering.id,
                periode = søknadsbehandlingVedtak.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = søknadsbehandlingVedtak.beregning,
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilBrevutsending = true,
            )
            testDataHelper.revurderingRepo.lagre(atteststertRevurdering)
            val iverksattRevurdering = IverksattRevurdering.IngenEndring(
                id = nyRevurdering.id,
                periode = søknadsbehandlingVedtak.periode,
                opprettet = nyRevurdering.opprettet,
                tilRevurdering = søknadsbehandlingVedtak,
                saksbehandler = søknadsbehandlingVedtak.saksbehandler,
                oppgaveId = OppgaveId(""),
                beregning = søknadsbehandlingVedtak.beregning,
                attestering = Attestering.Iverksatt(søknadsbehandlingVedtak.attestant),
                fritekstTilBrev = "",
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
                ),
                skalFøreTilBrevutsending = true,
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            val revurderingVedtak = Vedtak.from(iverksattRevurdering, fixedClock)

            vedtakRepo.lagre(revurderingVedtak)
            vedtakRepo.hent(revurderingVedtak.id) shouldBe revurderingVedtak

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
}
