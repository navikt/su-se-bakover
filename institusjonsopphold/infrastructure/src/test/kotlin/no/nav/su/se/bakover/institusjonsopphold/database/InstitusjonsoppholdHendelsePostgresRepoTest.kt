package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.InstitusjonsoppholdHendelse
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdHendelseDb.Companion.toDb
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId
import no.nav.su.se.bakover.test.nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InstitusjonsoppholdHendelsePostgresRepoTest {

    @Test
    fun `kaster exception ved dersom en ikkeKnyttetTilSak gjøres om til db type`() {
        assertThrows<IllegalStateException> {
            nyInstitusjonsoppholdHendelseIkkeTilknyttetTilSak().toDb()
        }
    }

    @Test
    fun `kan lagre hendelse uten oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val id = UUID.randomUUID()
            val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            testDataHelper.institusjonsoppholdHendelseRepo.lagre(
                nyInstitusjonsoppholdHendelseKnyttetTilSakUtenOppgaveId(
                    id = id,
                    sakId = sak.id,
                ),
            )
            testDataHelper.institusjonsoppholdHendelseRepo.hent(id).let {
                it shouldNotBe null
                it!!.id shouldBe id
                it.opprettet shouldBe fixedTidspunkt
                it.sakId shouldBe sak.id
                it.eksternHendelse.hendelseId shouldBe 1
                it.eksternHendelse.oppholdId shouldBe 1
                it.eksternHendelse.norskident shouldBe fnr
                it.eksternHendelse.type shouldBe InstitusjonsoppholdType.OPPDATERING
                it.eksternHendelse.kilde shouldBe InstitusjonsoppholdKilde.INST
                it.oppgaveId shouldBe null
            }
        }
    }

    @Test
    fun `kan lagre hendelse med oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val id = UUID.randomUUID()
            val (sak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget()

            testDataHelper.institusjonsoppholdHendelseRepo.lagre(
                nyInstitusjonsoppholdHendelseKnyttetTilSakMedOppgaveId(id = id, sakId = sak.id),
            )
            testDataHelper.institusjonsoppholdHendelseRepo.hent(id).let {
                it shouldNotBe null
                it!!.id shouldBe id
                it.opprettet shouldBe fixedTidspunkt
                it.sakId shouldBe sak.id
                it.eksternHendelse.hendelseId shouldBe 1
                it.eksternHendelse.oppholdId shouldBe 1
                it.eksternHendelse.norskident shouldBe fnr
                it.eksternHendelse.type shouldBe InstitusjonsoppholdType.OPPDATERING
                it.eksternHendelse.kilde shouldBe InstitusjonsoppholdKilde.INST
                it.oppgaveId shouldBe OppgaveId("oppgaveId")
            }
        }
    }

    @Test
    fun `henter alle hendelser uten oppgave id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterInstitusjonsoppholdHendelseTilknyttetSakUtenOppgaveId()
            testDataHelper.persisterInstitusjonsoppholdHendelseTilknyttetSakUtenOppgaveId()
            testDataHelper.persisterInstitusjonsoppholdHendelseTilknyttetSakMedOppgaveId()

            testDataHelper.institusjonsoppholdHendelseRepo.hentHendelserUtenOppgaveId().let {
                it.size shouldBe 2
                it.all { it is InstitusjonsoppholdHendelse.KnyttetTilSak.UtenOppgaveId }
            }
        }
    }
}
