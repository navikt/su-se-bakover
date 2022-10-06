package no.nav.su.se.bakover.database.klage

import arrow.core.right
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.klage.shouldBeEqualComparingPublicFieldsAndInterface
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.util.UUID

internal class KlagePostgresRepoTest {

    @Test
    fun `kan opprette og hente klager`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageRepo = testDataHelper.klagePostgresRepo

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageOpprettet()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageOpprettet().vilkårsvurder(
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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVilkårsvurdertUtfyltTilVurdering()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVilkårsvurdertUtfyltAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVilkårsvurdertBekreftetTilVurdering()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVilkårsvurdertBekreftetAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVilkårsvurdertBekreftetTilVurdering().vurder(
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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageVurdertUtfylt()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()
            val klage = testDataHelper.persisterKlageAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageTilAttesteringVurdert()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageTilAttesteringAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageUnderkjentVurdert()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageUnderkjentAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageOversendt()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageIverksattAvvist()

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

            val urelatertKlage = testDataHelper.persisterKlageOpprettet()

            val klage = testDataHelper.persisterKlageAvsluttet()

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

            val klage = testDataHelper.persisterKlageOversendt()
            val (klageinstanshendelseId, _) = testDataHelper.persisterUprosessertKlageinstanshendelse(klageId = klage.id)

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

            val klage = testDataHelper.persisterKlageOversendt()
            val (klageinstanshendelseId, _) = testDataHelper.persisterUprosessertKlageinstanshendelse(klageId = klage.id)

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
