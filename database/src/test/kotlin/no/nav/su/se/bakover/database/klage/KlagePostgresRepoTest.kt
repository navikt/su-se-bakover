package no.nav.su.se.bakover.database.klage

import arrow.core.right
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.UprosessertKlagevedtak
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlagePostgresRepoTest {

    @Test
    fun `kan opprette og hente klager`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.nyKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `vilkårsvurdert klage med alle felter null `() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.nyKlage().vilkårsvurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerPåbegyntVilkårsvurderinger"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
            ).also {
                klageRepo.lagre(it)
            }

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `vilkårsvurdert klage med alle felter satt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.utfyltVilkårsvurdertKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `vurdert klage med alle felter null`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.bekreftetVilkårsvurdertKlage().vurder(
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerPåbegyntVurderinger"),
                vurderinger = VurderingerTilKlage.empty(),
            ).also {
                klageRepo.lagre(it)
            }

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `vurdert klage med alle felter utfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.utfyltVurdertKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `klage til attestering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.klageTilAttestering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `underkjent klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.underkjentKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `iverksatt klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.oversendtKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `henter klagevedtak knyttet til klagen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val klage = testDataHelper.oversendtKlage()
            val (klagevedtakId, _) = testDataHelper.uprosessertKlagevedtak(klageId = klage.id)

            val uprosessertKlagevedtak = UprosessertKlagevedtak(
                id = klagevedtakId,
                opprettet = fixedTidspunkt,
                eventId = UUID.randomUUID().toString(),
                klageId = klage.id,
                utfall = KlagevedtakUtfall.RETUR,
                vedtaksbrevReferanse = UUID.randomUUID().toString()
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage = klage.leggTilNyttKlagevedtak(uprosessertKlagevedtak) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
                testDataHelper.klagevedtakPostgresRepo.lagre(uprosessertKlagevedtak.tilProsessert(nyOppgave), tx)
            }
            klageRepo.hentKlage(klage.id) shouldBe nyKlage
            klageRepo.hentKlager(klage.sakId) shouldBe listOf(nyKlage)
        }
    }

    @Test
    fun `henter kun prosesserte klagevedtak knyttet til klagen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val klage = testDataHelper.oversendtKlage()
            val (klagevedtakId, _) = testDataHelper.uprosessertKlagevedtak(klageId = klage.id)

            val uprosessertKlagevedtak = UprosessertKlagevedtak(
                id = klagevedtakId,
                opprettet = fixedTidspunkt,
                eventId = UUID.randomUUID().toString(),
                klageId = klage.id,
                utfall = KlagevedtakUtfall.RETUR,
                vedtaksbrevReferanse = UUID.randomUUID().toString()
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage = klage.leggTilNyttKlagevedtak(uprosessertKlagevedtak) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
            }

            klageRepo.hentKlage(klage.id)!!.klagevedtakshistorikk.shouldBeEmpty()
        }
    }
}
