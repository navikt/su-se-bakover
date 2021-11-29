package no.nav.su.se.bakover.database.klage

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test

internal class KlagePostgresRepoTest {

    @Test
    fun `kan opprette og hente klager`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo
            val sakRepo = testDataHelper.sakRepo

            opprettUrelatertSakMedKlage(sakRepo, klageRepo)

            val nySak =
                SakFactory(
                    clock = fixedClock,
                ).nySakMedNySøknad(
                    fnr = Fnr.generer(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                ).also {
                    sakRepo.opprettSak(it)
                }
            val klage: OpprettetKlage = Klage.ny(
                sakId = nySak.id,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                clock = fixedClock,
                datoKlageMottatt = 1.desember(2021),
            ).also {
                klageRepo.lagre(it)
            }

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(nySak.id, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
        }
    }

    @Test
    fun `vilkårsvurdert klage med alle felter null `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo
            val sakRepo = testDataHelper.sakRepo

            opprettUrelatertSakMedKlage(sakRepo, klageRepo)

            val nySak =
                SakFactory(
                    clock = fixedClock,
                ).nySakMedNySøknad(
                    fnr = Fnr.generer(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                ).also {
                    sakRepo.opprettSak(it)
                }
            val vilkårsvurdertKlage: VilkårsvurdertKlage.Påbegynt = Klage.ny(
                sakId = nySak.id,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                datoKlageMottatt = 1.desember(2021),
                clock = fixedClock,
            ).let { opprettetKlage ->
                klageRepo.lagre(opprettetKlage)
                opprettetKlage.vilkårsvurder(
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler2"),
                    vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
                ).orNull()!!.also {
                    klageRepo.lagre(it)
                }
            } as VilkårsvurdertKlage.Påbegynt

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(nySak.id, sessionContext) shouldBe listOf(vilkårsvurdertKlage)
            }
            klageRepo.hentKlage(vilkårsvurdertKlage.id) shouldBe vilkårsvurdertKlage
        }
    }

    @Test
    fun `vilkårsvurdert klage med alle felter satt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo
            val sakRepo = testDataHelper.sakRepo

            opprettUrelatertSakMedKlage(sakRepo, klageRepo)

            val (vedtak, utbetaling) = testDataHelper.vedtakMedInnvilgetSøknadsbehandling()

            val vilkårsvurdertKlage: VilkårsvurdertKlage.Utfylt = Klage.ny(
                sakId = utbetaling.sakId,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                datoKlageMottatt = 1.desember(2021),
                clock = fixedClock,
            ).let { opprettetKlage ->
                klageRepo.lagre(opprettetKlage)
                opprettetKlage.vilkårsvurder(
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler2"),
                    vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                        vedtakId = vedtak.id,
                        innenforFristen = true,
                        klagesDetPåKonkreteElementerIVedtaket = true,
                        erUnderskrevet = true,
                        begrunnelse = "enBegrunnelse",
                    ),
                ).orNull()!!.also {
                    klageRepo.lagre(it)
                }
            } as VilkårsvurdertKlage.Utfylt

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(utbetaling.sakId, sessionContext) shouldBe listOf(vilkårsvurdertKlage)
            }
            klageRepo.hentKlage(vilkårsvurdertKlage.id) shouldBe vilkårsvurdertKlage
        }
    }

    @Test
    fun `vurdert klage med alle felter null`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo
            val sakRepo = testDataHelper.sakRepo

            opprettUrelatertSakMedKlage(sakRepo, klageRepo)

            val (vedtak, utbetaling) = testDataHelper.vedtakMedInnvilgetSøknadsbehandling()

            val vurdertKlage: VurdertKlage.Påbegynt = Klage.ny(
                sakId = utbetaling.sakId,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                datoKlageMottatt = 1.desember(2021),
                clock = fixedClock,
            ).let { opprettetKlage ->
                klageRepo.lagre(opprettetKlage)

                val utfyltVilkårsvurdertKlage: VilkårsvurdertKlage.Utfylt = opprettetKlage.vilkårsvurder(
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler2"),
                    vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                        vedtakId = vedtak.id,
                        innenforFristen = true,
                        klagesDetPåKonkreteElementerIVedtaket = true,
                        erUnderskrevet = true,
                        begrunnelse = "enBegrunnelse",
                    ),
                ).orNull()!! as VilkårsvurdertKlage.Utfylt

                klageRepo.lagre(utfyltVilkårsvurdertKlage)

                val bekreftetVilkårsvurdertKlage: VilkårsvurdertKlage.Bekreftet =
                    utfyltVilkårsvurdertKlage.bekreftVilkårsvurderinger(
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler3"),
                    ).orNull()!!
                klageRepo.lagre(bekreftetVilkårsvurdertKlage)

                val vurdertKlage: VurdertKlage.Påbegynt = bekreftetVilkårsvurdertKlage.vurder(
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler3"),
                    vurderinger = VurderingerTilKlage.empty(),
                ).orNull()!! as VurdertKlage.Påbegynt

                klageRepo.lagre(vurdertKlage)
                vurdertKlage
            }

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(utbetaling.sakId, sessionContext) shouldBe listOf(vurdertKlage)
            }
            klageRepo.hentKlage(vurdertKlage.id) shouldBe vurdertKlage
        }
    }

    @Test
    fun `vurdert klage med alle felter utfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo
            val sakRepo = testDataHelper.sakRepo

            opprettUrelatertSakMedKlage(sakRepo, klageRepo)

            val (vedtak, utbetaling) = testDataHelper.vedtakMedInnvilgetSøknadsbehandling()

            val vurdertKlage: VurdertKlage.Utfylt = Klage.ny(
                sakId = utbetaling.sakId,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                datoKlageMottatt = 1.desember(2021),
                clock = fixedClock,
            ).let { opprettetKlage ->
                klageRepo.lagre(opprettetKlage)
                (
                    opprettetKlage.vilkårsvurder(
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler2"),
                        vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                            vedtakId = vedtak.id,
                            innenforFristen = true,
                            klagesDetPåKonkreteElementerIVedtaket = true,
                            erUnderskrevet = true,
                            begrunnelse = "enBegrunnelse",
                        ),
                    ).orNull()!! as VilkårsvurdertKlage.Utfylt
                    ).bekreftVilkårsvurderinger(NavIdentBruker.Saksbehandler(navIdent = "saksbehandler3")).orNull()!!
                    .let { vilkårsvurdertKlage ->
                        klageRepo.lagre(vilkårsvurdertKlage)
                        vilkårsvurdertKlage.vurder(
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler3"),
                            vurderinger = VurderingerTilKlage.create(
                                fritekstTilBrev = "Friteksten til brevet er som følge: ",
                                vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold(
                                    hjemler = Hjemler.Utfylt.create(
                                        nonEmptyListOf(Hjemmel.SU_PARAGRAF_3, Hjemmel.SU_PARAGRAF_4),
                                    ),
                                ),
                            ) as VurderingerTilKlage.Utfylt,
                        ).orNull()!!
                    }.also {
                        klageRepo.lagre(it)
                    }
            } as VurdertKlage.Utfylt

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(utbetaling.sakId, sessionContext) shouldBe listOf(vurdertKlage)
            }
            klageRepo.hentKlage(vurdertKlage.id) shouldBe vurdertKlage
        }
    }

    private fun opprettUrelatertSakMedKlage(
        sakRepo: SakRepo,
        klageRepo: KlagePostgresRepo,
    ) {
        SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr.generer(), SøknadInnholdTestdataBuilder.build()).also {
            sakRepo.opprettSak(it)
            Klage.ny(
                sakId = it.id,
                journalpostId = JournalpostId(value = "urelatertJournalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "urelatertSaksbehandler"),
                datoKlageMottatt = 1.desember(2021),
                clock = fixedClock,
            ).also { klage ->
                klageRepo.lagre(klage)
            }
        }
    }
}
