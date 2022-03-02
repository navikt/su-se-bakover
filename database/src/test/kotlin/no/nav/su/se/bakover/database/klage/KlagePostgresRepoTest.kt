package no.nav.su.se.bakover.database.klage

import arrow.core.right
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.klage.shouldBeEqualComparingPublicFieldsAndInterface
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
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
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
        }
    }

    @Test
    fun `kan lagre og hente en avsluttet klage`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.nyKlage()

            val klage = testDataHelper.avsluttetKlage()

            testDataHelper.sessionFactory.withSessionContext { sessionContext ->
                klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
            klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
        }
    }

    @Test
    fun `henter klageinstanshendelser knyttet til klagen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val klage = testDataHelper.oversendtKlage()
            val (klageinstanshendelseId, _) = testDataHelper.uprosessertKlageinstanshendelse(klageId = klage.id)

            val tolketKlageinstanshendelse = TolketKlageinstanshendelse(
                id = klageinstanshendelseId,
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = klage.id,
                utfall = KlageinstansUtfall.RETUR,
                journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage =
                klage.leggTilNyKlageinstanshendelse(tolketKlageinstanshendelse) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
                testDataHelper.klageinstanshendelsePostgresRepo.lagre(
                    tolketKlageinstanshendelse.tilProsessert(nyOppgave),
                    tx,
                )
            }
            klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(nyKlage)
            klageRepo.hentKlager(klage.sakId).shouldBeEqualComparingPublicFieldsAndInterface(listOf(nyKlage))
        }
    }

    @Test
    fun `henter kun prosesserte klageinstanshendelser knyttet til klagen`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val klage = testDataHelper.oversendtKlage()
            val (klageinstanshendelseId, _) = testDataHelper.uprosessertKlageinstanshendelse(klageId = klage.id)

            val tolketKlageinstanshendelse = TolketKlageinstanshendelse(
                id = klageinstanshendelseId,
                opprettet = fixedTidspunkt,
                avsluttetTidspunkt = fixedTidspunkt,
                klageId = klage.id,
                utfall = KlageinstansUtfall.RETUR,
                journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
            )

            val nyOppgave = OppgaveId("123")
            val nyKlage = klage.leggTilNyKlageinstanshendelse(tolketKlageinstanshendelse) { nyOppgave.right() }.getOrFail()

            testDataHelper.sessionFactory.withTransactionContext { tx ->
                klageRepo.lagre(nyKlage, tx)
            }

            val hentet = klageRepo.hentKlage(klage.id)!!
            hentet.shouldBeTypeOf<VurdertKlage.Bekreftet>()
            hentet.klageinstanshendelser.shouldBeEmpty()
        }
    }
}
