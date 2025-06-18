package no.nav.su.se.bakover.database.vedtak

import arrow.core.getOrElse
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.vedtak.VedtakEndringIYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtaksammendragForSak
import no.nav.su.se.bakover.domain.vedtak.Vedtakstype
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnrOver67
import no.nav.su.se.bakover.test.fnrUnder67
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.vedtak.vedtaksammendragForSakVedtak
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
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
    fun `hent alle aktive vedtak for måned`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = fixedClockAt(1.februar(2021)),
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
            vedtakRepo.hentForMåned(mars(2021)).also {
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
            vedtakRepo.hentForMåned(april(2021)) shouldBe emptyList()
        }
    }

    @Test
    fun `hent alle aktive vedtak fom dato eksl eps`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = fixedClockAt(1.februar(2021)),
            )
            val vedtakRepo = testDataHelper.vedtakRepo
            val (_, _, vedtakJan) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(januar(2021)),
                    sakOgSøknad = sak to søknad,
                )
            }
            val (_, _, vedtakFebMars) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(Periode.create(1.februar(2021), 31.mars(2021))),
                    sakOgSøknad = sak to søknad,
                )
            }
            vedtakRepo.hentForFraOgMedMånedEksEps(januar(2021)).also {
                it.size shouldBe 2
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakJan.behandling.fnr,
                    sakId = vedtakJan.sakId,
                    saksnummer = vedtakJan.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakJan.opprettet,
                            periode = vedtakJan.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
                it[1] shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakFebMars.behandling.fnr,
                    sakId = vedtakFebMars.sakId,
                    saksnummer = vedtakFebMars.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakFebMars.opprettet,
                            periode = vedtakFebMars.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
            }
            vedtakRepo.hentForFraOgMedMånedEksEps(februar(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakFebMars.behandling.fnr,
                    sakId = vedtakFebMars.sakId,
                    saksnummer = vedtakFebMars.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakFebMars.opprettet,
                            periode = vedtakFebMars.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
            }
            vedtakRepo.hentForFraOgMedMånedEksEps(mars(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakFebMars.behandling.fnr,
                    sakId = vedtakFebMars.sakId,
                    saksnummer = vedtakFebMars.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakFebMars.opprettet,
                            periode = vedtakFebMars.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
            }
            vedtakRepo.hentForFraOgMedMånedEksEps(april(2021)) shouldBe emptyList()
        }
    }

    @Test
    fun `hent alle aktive vedtak fom dato inkl eps`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(
                dataSource = dataSource,
                clock = TikkendeKlokke(fixedClockAt(1.februar(2021))),
            )
            val vedtakRepo = testDataHelper.vedtakRepo
            val (_, _, vedtakJan) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    clock = testDataHelper.clock,
                    stønadsperiode = Stønadsperiode.create(januar(2021)),
                    sakOgSøknad = sak to søknad,
                )
            }

            val fnrEpsFeb = Fnr("11111111111")
            val fnrEpsMars = Fnr("22222222222")

            val (sak: Sak, _, _, revurderingsvedtak: VedtakEndringIYtelse) = testDataHelper.persisterIverksattRevurdering(
                stønadsperiode = Stønadsperiode.create(februar(2021)..mars(2021)),
                revurderingsperiode = februar(2021)..mars(2021),
                grunnlagsdataOverrides = listOf(
                    bosituasjonEpsUnder67(
                        opprettet = Tidspunkt.now(testDataHelper.clock),
                        fnr = fnrEpsFeb,
                        periode = februar(2021),
                    ),
                    bosituasjonEpsUnder67(
                        opprettet = Tidspunkt.now(testDataHelper.clock),
                        fnr = fnrEpsMars,
                        periode = mars(2021),
                    ),
                ),
                vilkårOverrides = listOf(
                    formuevilkårMedEps0Innvilget(
                        opprettet = Tidspunkt.now(testDataHelper.clock),
                        periode = februar(2021)..mars(2021),
                    ),
                ),
            )
            val søknadsbehandlingsvedtak = sak.vedtakListe.filterIsInstance<VedtakInnvilgetSøknadsbehandling>().single()

            vedtakRepo.hentForFraOgMedMånedInklEps(januar(2021)).also {
                it.size shouldBe 2
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = vedtakJan.behandling.fnr,
                    sakId = vedtakJan.sakId,
                    saksnummer = vedtakJan.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtakJan.opprettet,
                            periode = vedtakJan.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                        ),
                    ),
                )
                it[1] shouldBe VedtaksammendragForSak(
                    fødselsnummer = søknadsbehandlingsvedtak.fnr,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = søknadsbehandlingsvedtak.opprettet,
                            periode = søknadsbehandlingsvedtak.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                            epsFnr = emptyList(),
                        ),
                        vedtaksammendragForSakVedtak(
                            opprettet = revurderingsvedtak.opprettet,
                            periode = revurderingsvedtak.periode,
                            vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                            epsFnr = listOf(fnrEpsFeb, fnrEpsMars),
                        ),
                    ),
                )
            }

            vedtakRepo.hentForFraOgMedMånedInklEps(februar(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = søknadsbehandlingsvedtak.fnr,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = søknadsbehandlingsvedtak.opprettet,
                            periode = søknadsbehandlingsvedtak.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                            epsFnr = emptyList(),
                        ),
                        vedtaksammendragForSakVedtak(
                            opprettet = revurderingsvedtak.opprettet,
                            periode = revurderingsvedtak.periode,
                            vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                            epsFnr = listOf(fnrEpsFeb, fnrEpsMars),
                        ),
                    ),
                )
            }

            vedtakRepo.hentForFraOgMedMånedInklEps(mars(2021)).also {
                it.size shouldBe 1
                it.first() shouldBe VedtaksammendragForSak(
                    fødselsnummer = søknadsbehandlingsvedtak.fnr,
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    vedtak = listOf(
                        vedtaksammendragForSakVedtak(
                            opprettet = søknadsbehandlingsvedtak.opprettet,
                            periode = søknadsbehandlingsvedtak.periode,
                            vedtakstype = Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE,
                            epsFnr = emptyList(),
                        ),
                        vedtaksammendragForSakVedtak(
                            opprettet = revurderingsvedtak.opprettet,
                            periode = revurderingsvedtak.periode,
                            vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                            epsFnr = listOf(fnrEpsFeb, fnrEpsMars),
                        ),
                    ),
                )
            }

            vedtakRepo.hentForFraOgMedMånedInklEps(april(2021)) shouldBe emptyList()
        }
    }

    @Test
    fun `henter alle vedtak knyttet til bruker som kun har en søknad som er innsendt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource)
            val fnr = Fnr.generer()
            testDataHelper.persisterJournalførtSøknadMedOppgave(fnr = fnr)
            testDataHelper.vedtakRepo.hentForBrukerFødselsnumreOgFraOgMedMåned(
                fødselsnumre = listOf(fnr),
                fraOgMed = januar(2021),
            ) shouldBe emptyList()
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
                            epsFnr = listOf(fnrUnder67),
                        ),
                        vedtaksammendragForSakVedtak(
                            opprettet = vedtak1_2.opprettet,
                            periode = vedtak1_2.periode,
                            vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                            epsFnr = listOf(fnrOver67),
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
                            epsFnr = listOf(sak2EpsFnr),
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
                            epsFnr = listOf(fnrUnder67),
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

            val epsFnrJanJuni = Fnr("11111111111")
            val epsFnrJuliDes = Fnr("22222222222")

            val bosituasjonFørsteHalvdel = bosituasjonEpsUnder67(
                periode = januar(2021)..juni(2021),
                opprettet = Tidspunkt.now(clock),
                fnr = epsFnrJanJuni,
            )
            val bosituasjonAndreHalvdel = bosituasjonEpsUnder67(
                periode = juli(2021)..desember(2021),
                opprettet = Tidspunkt.now(clock),
                fnr = epsFnrJuliDes,
            )
            val (sakFørRevurdering, _, vedtakSøknadsbehandling) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()
            val (sakEtterRevurdering, _, _, vedtakRevurdering) = testDataHelper.persisterIverksattRevurdering(
                sakOgVedtak = (sakFørRevurdering to vedtakSøknadsbehandling),
                grunnlagsdataOverrides = listOf(
                    bosituasjonFørsteHalvdel,
                    bosituasjonAndreHalvdel,
                ),
            )
            fun expected(epsFnr: List<Fnr>) = VedtaksammendragForSak(
                fødselsnummer = sakEtterRevurdering.fnr,
                sakId = sakEtterRevurdering.id,
                saksnummer = sakEtterRevurdering.saksnummer,
                vedtak = listOf(
                    vedtaksammendragForSakVedtak(
                        opprettet = vedtakRevurdering.opprettet,
                        periode = vedtakRevurdering.periode,
                        vedtakstype = Vedtakstype.REVURDERING_INNVILGELSE,
                        epsFnr = epsFnr,
                    ),
                ),
            )
            // Dette er før stønadsperioden og vi forventer få med begge EPS
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                desember(2020),
            ) shouldBe listOf(expected(listOf(epsFnrJanJuni, epsFnrJuliDes)))

            // Dette er før stønadsperioden og vi forventer få med første EPS
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni),
                desember(2020),
            ) shouldBe listOf(expected(listOf(epsFnrJanJuni)))

            // Dette er før stønadsperioden og vi forventer få med siste EPS
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJuliDes),
                desember(2020),
            ) shouldBe listOf(expected(listOf(epsFnrJuliDes)))

            // Dette er første måned i stønadsperioden og vi forventer få med alt
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                januar(2021),
            ) shouldBe listOf(expected(listOf(epsFnrJanJuni, epsFnrJuliDes)))

            // Dette er siste måned for første EPS og vi forventer få med alt
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                juni(2021),
            ) shouldBe listOf(expected(listOf(epsFnrJanJuni, epsFnrJuliDes)))

            // Dette er første måned for andre EPS og vi forventer få med andre EPS
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                juli(2021),
            ) shouldBe listOf(expected(listOf(epsFnrJuliDes)))

            // Dette er siste måned i stønadsperioden og vi forventer få med andre EPS
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                desember(2021),
            ) shouldBe listOf(expected(listOf(epsFnrJuliDes)))

            // Dette er første måned etter stønadsperioden og vi forventer tomt resultat
            repo.hentForEpsFødselsnumreOgFraOgMedMåned(
                listOf(epsFnrJanJuni, epsFnrJuliDes),
                januar(2022),
            ) shouldBe emptyList()
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
                    generertDokument = minimumPdfAzeroPadded(),
                    generertDokumentJson = """{"some": "json"}""",
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        vedtakId = vedtak.id,
                    ),
                    distribueringsadresse = null,
                )
                dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())
                val journalført = dokumentRepo.hentDokumenterForJournalføring().first()
                dokumentRepo.oppdaterDokumentdistribusjon(
                    journalført.journalfør { JournalpostId("jp").right() }.getOrElse {
                        fail { "Skulle fått journalført" }
                    }.distribuerBrev(testDataHelper.clock) { BrevbestillingId("brev").right() }.getOrElse {
                        fail { "Skulle fått bestilt brev" }
                    },
                )
                testDataHelper.vedtakRepo.hentJournalpostId(vedtak.id) shouldBe JournalpostId("jp")
            }
        }
    }
}
