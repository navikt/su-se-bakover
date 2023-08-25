package no.nav.su.se.bakover.database.vedtak

import arrow.core.getOrElse
import arrow.core.right
import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtaksammendrag
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.plus
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakPostgresRepoTest {

    @Test
    fun `setter inn og henter vedtak for innvilget stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `setter inn og henter vedtak for avslått stønad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            val (sak, _, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()

            dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
                sak.vedtakListe.single() shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            dataSource.withSession { session ->
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
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, søknadsbehandlingVedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
                .let { it.first to it.second }

            val (_, iverksattRevurdering, revurderingUtbetaling, revurderingVedtak) = iverksattRevurdering(
                clock = testDataHelper.clock,
                sakOgVedtakSomKanRevurderes = sak to søknadsbehandlingVedtak,
            )
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)
            testDataHelper.utbetalingRepo.opprettUtbetaling(revurderingUtbetaling)
            testDataHelper.vedtakRepo.lagre(revurderingVedtak)

            dataSource.withSession { session ->
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
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = fixedClock.plus(31, ChronoUnit.DAYS),
            )
            val vedtakRepo = testDataHelper.vedtakRepo
            // Persisterer et ikke-aktivt vedtak
            testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(januar(2021)),
                    sakOgSøknad = sak to søknad,
                )
            }
            val (_, _, vedtakSomErAktivt) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(Periode.create(1.februar(2021), 31.mars(2021))),
                    sakOgSøknad = sak to søknad,
                )
            }

            vedtakRepo.hentForMåned(februar(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe Vedtaksammendrag(
                    opprettet = vedtakSomErAktivt.opprettet,
                    periode = vedtakSomErAktivt.periode,
                    fødselsnummer = vedtakSomErAktivt.behandling.fnr,
                    vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                    sakId = vedtakSomErAktivt.sakId,
                    saksnummer = vedtakSomErAktivt.saksnummer,
                )
            }
        }
    }

    @Test
    fun `oppdaterer koblingstabell mellom søknadsbehandling og vedtak ved lagring av vedtak for avslått søknadsbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, søknadsbehandling, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()

            dataSource.withSession { session ->
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
    fun `oppretter og henter vedtak for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (_, vedtak) = testDataHelper.persisterIverksattStansOgVedtak()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Test
    fun `oppretter og henter vedtak for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak = testDataHelper.persisterVedtakForGjenopptak()
            val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
            testDataHelper.dataSource.withSession {
                vedtakRepo.hentVedtakForIdOgSession(vedtak.id, it) shouldBe vedtak
            }
        }
    }

    @Nested
    inner class hentSøknadsbehandlingsvedtakFraOgMed {

        @Test
        fun `fraOgMed før`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val vedtak = testDataHelper.persisterSøknadsbehandlingIverksatt()
                val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
                testDataHelper.dataSource.withSession {
                    vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(31.desember(2020)) shouldBe listOf(vedtak.third.id)
                }
            }
        }

        @Test
        fun `fraOgMed på`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val vedtak = testDataHelper.persisterSøknadsbehandlingIverksatt()
                val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
                testDataHelper.dataSource.withSession {
                    vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(1.januar(2021)) shouldBe listOf(vedtak.third.id)
                }
            }
        }

        @Test
        fun `fraOgMed etter`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                testDataHelper.persisterSøknadsbehandlingIverksatt()
                val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
                testDataHelper.dataSource.withSession {
                    vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(2.januar(2021)) shouldBe emptyList()
                }
            }
        }

        @Test
        fun `ignorerer vedtak som ikke er søknadsbehandling`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val første = testDataHelper.persisterRevurderingIverksattInnvilget().let {
                    it.first.vedtakListe.first() as VedtakIverksattSøknadsbehandling
                }
                val andre = testDataHelper.persisterIverksattStansOgVedtak().let {
                    it.first.vedtakListe.first() as VedtakIverksattSøknadsbehandling
                }

                val vedtakRepo = testDataHelper.vedtakRepo as VedtakPostgresRepo
                testDataHelper.dataSource.withSession {
                    vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(1.januar(2021)) shouldBe listOf(
                        første.id,
                        andre.id,
                    )
                }
            }
        }

        @Test
        fun `henter journalpost id`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val dokumentRepo = testDataHelper.databaseRepos.dokumentRepo

                val (sak, vedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()

                val original = Dokument.MedMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    tittel = "tittel",
                    generertDokument = pdfATom(),
                    generertDokumentJson = """{"some": "json"}""",
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        vedtakId = vedtak.id,
                    ),
                )
                dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())
                val journalført = dokumentRepo.hentDokumenterForJournalføring().first()
                dokumentRepo.oppdaterDokumentdistribusjon(
                    journalført.journalfør { JournalpostId("jp").right() }.getOrElse {
                        fail { "Skulle fått journalført" }
                    }.distribuerBrev { BrevbestillingId("brev").right() }.getOrElse {
                        fail { "Skulle fått bestilt brev" }
                    },
                )
                testDataHelper.vedtakRepo.hentJournalpostId(vedtak.id) shouldBe JournalpostId("jp")
            }
        }
    }
}
