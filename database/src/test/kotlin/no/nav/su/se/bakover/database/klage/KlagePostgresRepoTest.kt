package no.nav.su.se.bakover.database.klage

import arrow.core.right
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.VurderingerTilKlage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.klage.shouldBeEqualComparingPublicFieldsAndInterface
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class KlagePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `kan opprette og hente klager`() {
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

    @Test
    fun `vilkårsvurdert klage med alle felter null `() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val urelatertKlage = testDataHelper.persisterKlageOpprettet()

        val klage = testDataHelper.persisterKlageOpprettet().vilkårsvurder(
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlerPåbegyntVilkårsvurderinger"),
            vilkårsvurderinger = FormkravTilKlage.empty(),
        ).getOrFail().also {
            klageRepo.lagre(it)
        }

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
        klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
    }

    @Test
    fun `kan lagre utfyltVilkårsvurdertTilVurdering`() {
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

    @Test
    fun `kan lagre utfyltVilkårsvurdertAvvist`() {
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

    @Test
    fun `kan lagre bekreftetVilkårsvurdertTilVurdering`() {
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

    @Test
    fun `kan lagre bekreftetVilkårsvurdertAvvist`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val urelatertKlage = testDataHelper.persisterKlageOpprettet()

        val klage = testDataHelper.persisterKlageVilkårsvurdertBekreftetAvvist()

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            val klager = klageRepo.hentKlager(klage.sakId, sessionContext)
            klager.shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
        klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
    }

    @Test
    fun `vurdert klage med alle felter null`() {
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

    @Test
    fun `vurdert klage med alle felter utfylt`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val urelatertKlage = testDataHelper.persisterKlageOpprettet()

        val klage = testDataHelper.persisterKlageVurdertUtfyltTilOversending()

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
        klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
    }

    @Test
    fun `lagrer avvist klage`() {
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

    @Test
    fun `klage til attestering til vurdering`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val urelatertKlage = testDataHelper.persisterKlageOpprettet()

        val klage = testDataHelper.persisterKlageTilAttesteringVurdert(erOppretthold = true)

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        val hentetKlage = klageRepo.hentKlage(klage.id)
        hentetKlage.shouldBeEqualComparingPublicFieldsAndInterface(klage)
        val attestertKlage = hentetKlage as KlageTilAttestering.Vurdert
        attestertKlage.vurderinger.shouldBeInstanceOf<VurderingerTilKlage.UtfyltOppretthold>()
        klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)
    }

    @Test
    fun `klage til attestering til vurdering for delvis omgjøring`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val klage = testDataHelper.persisterKlageTilAttesteringVurdert(erOppretthold = false)

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        val hentetKlage = klageRepo.hentKlage(klage.id)
        hentetKlage.shouldBeEqualComparingPublicFieldsAndInterface(klage)
        val attestertKlage = hentetKlage as KlageTilAttestering.Vurdert
        attestertKlage.vurderinger.shouldBeInstanceOf<VurderingerTilKlage.UtfyltDelvisOmgjøringKA>()
    }

    @Test
    fun `kan lagre avvist klage til attestering`() {
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

    @Test
    fun `underkjent klage til vurdering`() {
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

    @Test
    fun `lagrer avvist underkjent klage`() {
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

    @Test
    fun `oversendt klage`() {
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

    @Test
    fun `oversendt klage med delvis omgjøring`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val klage = testDataHelper.persisterKlageOversendt()

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
    }

    @Test
    fun `knytt behandling mot oversendt klage`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val urelatertKlage = testDataHelper.persisterKlageOpprettet()

        val klage = testDataHelper.persisterKlageOversendt()

        testDataHelper.sessionFactory.withSessionContext { sessionContext ->
            klageRepo.hentKlager(klage.sakId, sessionContext).shouldBeEqualComparingPublicFieldsAndInterface(listOf(klage))
        }
        klageRepo.hentKlage(klage.id).shouldBeEqualComparingPublicFieldsAndInterface(klage)
        klageRepo.hentKlage(urelatertKlage.id).shouldBeEqualComparingPublicFieldsAndInterface(urelatertKlage)

        val behandlingId = UUID.randomUUID()
        klageRepo.knyttMotOmgjøring(klage.id, behandlingId = behandlingId)
        val knyttetOverSendtKlage = klageRepo.hentKlage(klage.id)
        knyttetOverSendtKlage.shouldNotBeNull()
        knyttetOverSendtKlage.shouldBeInstanceOf<OversendtKlage>()
        knyttetOverSendtKlage.behandlingId shouldBe behandlingId
    }

    @Test
    fun `lagrer en iverksatt avvist klage`() {
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

    @Test
    fun `kan lagre og hente en avsluttet klage`() {
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

    @Test
    fun `henter klageinstanshendelser knyttet til klagen`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val klage = testDataHelper.persisterKlageOversendt()
        val (klageinstanshendelseId, _) = testDataHelper.persisterUprosessertKlageinstanshendelse(klageId = klage.id)

        val tolketKlageinstanshendelse = TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
            id = klageinstanshendelseId,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = AvsluttetKlageinstansUtfall.Retur,
            journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
        )

        val nyOppgave = oppgaveId
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

    @Test
    fun `henter kun prosesserte klageinstanshendelser knyttet til klagen`() {
        val testDataHelper = TestDataHelper(dataSource)
        val klageRepo = testDataHelper.klagePostgresRepo

        val klage = testDataHelper.persisterKlageOversendt()
        val (klageinstanshendelseId, _) = testDataHelper.persisterUprosessertKlageinstanshendelse(klageId = klage.id)

        val tolketKlageinstanshendelse = TolketKlageinstanshendelse.KlagebehandlingAvsluttet(
            id = klageinstanshendelseId,
            opprettet = fixedTidspunkt,
            avsluttetTidspunkt = fixedTidspunkt,
            klageId = klage.id,
            utfall = AvsluttetKlageinstansUtfall.Retur,
            journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
        )

        val nyOppgave = oppgaveId
        val nyKlage = klage.leggTilNyKlageinstanshendelse(tolketKlageinstanshendelse) { nyOppgave.right() }.getOrFail()

        testDataHelper.sessionFactory.withTransactionContext { tx ->
            klageRepo.lagre(nyKlage, tx)
        }

        val hentet = klageRepo.hentKlage(klage.id)!!
        hentet.shouldBeTypeOf<VurdertKlage.BekreftetTilOversending>()
        hentet.klageinstanshendelser.shouldBeEmpty()
    }
}
