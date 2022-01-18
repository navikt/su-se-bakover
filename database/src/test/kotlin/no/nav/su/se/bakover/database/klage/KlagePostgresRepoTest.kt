package no.nav.su.se.bakover.database.klage

import arrow.core.right
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KlagevedtakUtfall
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
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
            ).getOrFail().also {
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
    fun `kan lagre utfyltVilkårsvurdertTilVurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.utfyltVilkårsvurdertKlageTilVurdering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `kan lagre utfyltVilkårsvurdertAvvist`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.utfyltAvvistVilkårsvurdertKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `kan lagre bekreftetVilkårsvurdertTilVurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.bekreftetVilkårsvurdertKlageTilVurdering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `kan lagre bekreftetVilkårsvurdertAvvist`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.bekreftetAvvistVilkårsvurdertKlage()

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

            val klage = testDataHelper.bekreftetVilkårsvurdertKlageTilVurdering().vurder(
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
    fun `lagrer avvist klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()
            val klage = testDataHelper.avvistKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `klage til attestering til vurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.klageTilAttesteringTilVurdering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `kan lagre avvist klage til attestering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.avvistKlageTilAttestering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `underkjent klage til vurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.underkjentKlageTilVurdering()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `lagrer avvist underkjent klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.underkjentAvvistKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext) shouldBe listOf(klage)
            }
            klageRepo.hentKlage(klage.id) shouldBe klage
            klageRepo.hentKlage(urelatertKlage.id) shouldBe urelatertKlage
        }
    }

    @Test
    fun `oversendt klage`() {
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
    fun `lagrer en iverksatt avvist klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.iverksattAvvistKlage()

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

            val uprosessertKlageinstansVedtak = UprosessertKlageinstansvedtak(
                id = klagevedtakId,
                opprettet = fixedTidspunkt,
                klageId = klage.id,
                utfall = KlagevedtakUtfall.RETUR,
                vedtaksbrevReferanse = UUID.randomUUID().toString()
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage = klage.leggTilNyttKlagevedtak(uprosessertKlageinstansVedtak) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
                testDataHelper.klagevedtakPostgresRepo.lagre(uprosessertKlageinstansVedtak.tilProsessert(nyOppgave), tx)
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

            val uprosessertKlageinstansVedtak = UprosessertKlageinstansvedtak(
                id = klagevedtakId,
                opprettet = fixedTidspunkt,
                klageId = klage.id,
                utfall = KlagevedtakUtfall.RETUR,
                vedtaksbrevReferanse = UUID.randomUUID().toString(),
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage = klage.leggTilNyttKlagevedtak(uprosessertKlageinstansVedtak) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
            }

            val hentet = klageRepo.hentKlage(klage.id)!!
            hentet.shouldBeTypeOf<VurdertKlage.Bekreftet>()
            hentet.klagevedtakshistorikk.shouldBeEmpty()
        }
    }
}
