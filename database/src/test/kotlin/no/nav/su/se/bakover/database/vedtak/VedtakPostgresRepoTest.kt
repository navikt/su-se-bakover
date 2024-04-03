package no.nav.su.se.bakover.database.vedtak

import arrow.core.getOrElse
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fnrUnder67
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSakVedtak
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
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
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakSomErAktivt.behandling.fnr,
                    sakId = vedtakSomErAktivt.sakId,
                    saksnummer = vedtakSomErAktivt.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakSomErAktivt.opprettet,
                            periode = vedtakSomErAktivt.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `henter alle vedtak knyttet til bruker fra en måned med fødselsnummere som er supplert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource)
            val (sak, _, _, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            testDataHelper.vedtakRepo.hentForBrukerFødselsnumreOgFraOgMedMåned(
                fødselsnumre = listOf(sak.fnr),
                fraOgMed = januar(2021),
            ).size shouldBe 1
        }
    }

    @Test
    fun `tar ikke med vedtak dersom fraOgMed er etter bruker sin vedtaksperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.vedtakRepo

            val (sak, _, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            val expected = VedtaksammendragForSak(
                fødselsnummer = sak.fnr,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                vedtak = listOf(
                    vedtaksammendragForSakVedtak(
                        opprettet = vedtak.opprettet,
                        periode = vedtak.periode,
                        vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                    ),
                ),
            )
            repo.hentForBrukerFødselsnumreOgFraOgMedMåned(listOf(sak.fnr), januar(2022)) shouldBe emptyList()

            repo.hentForBrukerFødselsnumreOgFraOgMedMåned(
                listOf(sak.fnr),
                juli(2021),
            ) shouldBe listOf(expected)

            repo.hentForBrukerFødselsnumreOgFraOgMedMåned(
                listOf(sak.fnr),
                desember(2020),
            ) shouldBe listOf(expected)

            repo.hentForBrukerFødselsnumreOgFraOgMedMåned(
                listOf(sak.fnr, sak.fnr),
                juli(2021),
            ) shouldBe listOf(expected)
        }
    }

    @Test
    fun `henter alle vedtak knyttet til eps fra en måned med fødselsnummere som er supplert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val clock = testDataHelper.clock
            val repo = testDataHelper.vedtakRepo

            val (sak1, _, vedtak1_1) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget(
                grunnlagsdataOverrides = listOf(
                    bosituasjonEpsUnder67(opprettet = Tidspunkt.now(clock), fnr = fnrUnder67),
                ),
                vilkårOverrides = listOf(formuevilkårMedEps0Innvilget(opprettet = Tidspunkt.now(clock))),
            )
            val (_, _, _, vedtak1_2) = testDataHelper.persisterIverksattRevurdering(
                sakOgVedtak = (sak1 to vedtak1_1),
                grunnlagsdataOverrides = listOf(
                    bosituasjonEpsOver67(opprettet = Tidspunkt.now(clock), fnr = fnrOver67),
                ),
                vilkårOverrides = listOf(formuevilkårMedEps0Innvilget(opprettet = Tidspunkt.now(clock))),
            )

            val sak2EpsFnr = Fnr.generer()
            val (sak2, _, vedtak2, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget(
                grunnlagsdataOverrides = listOf(
                    bosituasjonEpsUnder67(opprettet = Tidspunkt.now(clock), fnr = sak2EpsFnr),
                ),
                vilkårOverrides = listOf(formuevilkårMedEps0Innvilget(opprettet = Tidspunkt.now(clock))),
            )

            // saken blir knyttet til samme eps fnr som første
            val (sak3, _, vedtak3) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget(
                grunnlagsdataOverrides = listOf(
                    bosituasjonEpsUnder67(opprettet = Tidspunkt.now(clock), fnr = fnrUnder67),
                ),
                vilkårOverrides = listOf(formuevilkårMedEps0Innvilget(opprettet = Tidspunkt.now(clock))),
            )

            val fnrs = listOf(fnrUnder67, fnrOver67, sak2EpsFnr)
            val fraMåned = januar(2021)
            val actual = repo.hentForEpsFødselsnumreOgFraOgMedMåned(fnrs, fraMåned)
            actual shouldBe listOf(
                VedtaksammendragForSak(
                    fødselsnummer = sak1.fnr,
                    sakId = sak1.id,
                    saksnummer = sak1.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtak1_1.opprettet,
                            periode = vedtak1_1.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtak1_2.opprettet,
                            periode = vedtak1_2.periode,
                            vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                        ),
                    ),
                ),
                VedtaksammendragForSak(
                    fødselsnummer = sak2.fnr,
                    sakId = sak2.id,
                    saksnummer = sak2.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtak2.opprettet,
                            periode = vedtak2.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                ),
                VedtaksammendragForSak(
                    fødselsnummer = sak3.fnr,
                    sakId = sak3.id,
                    saksnummer = sak3.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtak3.opprettet,
                            periode = vedtak3.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `tar ikke med vedtak dersom fraOgMed er etter eps sin bosituasjonsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val clock = testDataHelper.clock
            val repo = testDataHelper.vedtakRepo

            val fnrFørsteHalvdel = Fnr.generer()
            val fnrAndreHalvdel = Fnr.generer()

            val bosituasjonFørsteHalvdel = bosituasjonEpsUnder67(
                periode = januar(2021)..juni(2021),
                opprettet = Tidspunkt.now(clock),
                fnr = fnrFørsteHalvdel,
            )
            val bosituasjonAndreHalvdel = bosituasjonEpsUnder67(
                periode = juli(2021)..desember(2021),
                opprettet = Tidspunkt.now(clock),
                fnr = fnrAndreHalvdel,
            )
            val (sakFørRevurdering, _, vedtakSøknadsbehandling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
            val (sakEtterRevurdering, _, _, vedtakRevurdering) = testDataHelper.persisterIverksattRevurdering(
                sakOgVedtak = (sakFørRevurdering to vedtakSøknadsbehandling),
                grunnlagsdataOverrides = listOf(
                    bosituasjonFørsteHalvdel,
                    bosituasjonAndreHalvdel,
                ),
            )

            val expected = VedtaksammendragForSak(
                fødselsnummer = sakEtterRevurdering.fnr,
                sakId = sakEtterRevurdering.id,
                saksnummer = sakEtterRevurdering.saksnummer,
                vedtak = listOf(
                    vedtaksammendragForSakVedtak(
                        opprettet = vedtakRevurdering.opprettet,
                        periode = vedtakRevurdering.periode,
                        vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                    ),
                ),
            )
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(listOf(fnrFørsteHalvdel), juli(2021)) shouldBe emptyList()

            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(fnrFørsteHalvdel, fnrAndreHalvdel),
                juli(2021),
            ) shouldBe listOf(expected)

            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(fnrFørsteHalvdel, fnrAndreHalvdel),
                desember(2020),
            ) shouldBe listOf(expected)

            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(fnrFørsteHalvdel, fnrAndreHalvdel),
                januar(2022),
            ) shouldBe emptyList()

            repo.hentForEpsFødselsnumreOgFraOgMedMåned(listOf(fnrFørsteHalvdel), juni(2021)) shouldBe listOf(
                expected,
            )

            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(fnrFørsteHalvdel, fnrAndreHalvdel),
                juni(2021),
            ) shouldBe listOf(expected)
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
