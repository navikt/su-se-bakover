package no.nav.su.se.bakover.database.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test

internal class KlagePostgresRepoTest {

    @Test
    fun `kan opprette og hente klager`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.klagePostgresRepo

            // Oppretter en urelatert sak med klage
            SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr.generer(), SøknadInnholdTestdataBuilder.build()).also {
                testDataHelper.sakRepo.opprettSak(it)
                Klage.ny(
                    sakId = it.id,
                    journalpostId = JournalpostId(value = "urelatertJournalpostId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "urelatertSaksbehandler"),
                    clock = fixedClock,
                ).also { klage ->
                    repo.lagre(klage)
                }
            }

            val nySak =
                SakFactory(
                    clock = fixedClock,
                ).nySakMedNySøknad(
                    fnr = Fnr.generer(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                ).also {
                    testDataHelper.sakRepo.opprettSak(it)
                }
            val klage = Klage.ny(
                sakId = nySak.id,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                clock = fixedClock,
            ).also {
                repo.lagre(it)
            }

            dataSource.withSession { session ->
                repo.hentKlager(nySak.id, session) shouldBe listOf(klage)
            }
            repo.hentKlage(klage.id) shouldBe klage
        }
    }

    @Test
    fun `vilkårsvurdert klage med alle felter null `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.klagePostgresRepo

            // Oppretter en urelatert sak med klage
            SakFactory(clock = fixedClock).nySakMedNySøknad(Fnr.generer(), SøknadInnholdTestdataBuilder.build()).also {
                testDataHelper.sakRepo.opprettSak(it)
                Klage.ny(
                    sakId = it.id,
                    journalpostId = JournalpostId(value = "urelatertJournalpostId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "urelatertSaksbehandler"),
                    clock = fixedClock,
                ).also { klage ->
                    repo.lagre(klage)
                }
            }

            val nySak =
                SakFactory(
                    clock = fixedClock,
                ).nySakMedNySøknad(
                    fnr = Fnr.generer(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                ).also {
                    testDataHelper.sakRepo.opprettSak(it)
                }
            val vilkårsvurdertKlage: VilkårsvurdertKlage = Klage.ny(
                sakId = nySak.id,
                journalpostId = JournalpostId(value = "journalpostId"),
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                clock = fixedClock,
            ).let { opprettetKlage ->
                repo.lagre(opprettetKlage)
                opprettetKlage.vilkårsvurder(
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler2"),
                    vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt(
                        vedtakId = null,
                        innenforFristen = null,
                        klagesDetPåKonkreteElementerIVedtaket = null,
                        erUnderskrevet = null,
                        begrunnelse = null
                    )
                ).orNull()!!.also {
                    repo.lagre(it)
                }
            }

            dataSource.withSession { session ->
                repo.hentKlager(nySak.id, session) shouldBe listOf(vilkårsvurdertKlage)
            }
            repo.hentKlage(vilkårsvurdertKlage.id) shouldBe vilkårsvurdertKlage
        }
    }
}
