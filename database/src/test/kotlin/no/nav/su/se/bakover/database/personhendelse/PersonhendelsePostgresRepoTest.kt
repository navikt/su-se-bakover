package no.nav.su.se.bakover.database.personhendelse

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

internal class PersonhendelsePostgresRepoTest {

    private val hendelseId = UUID.randomUUID().toString()
    private val aktørId = "abcdefghjiklm"
    private val fnr = Fnr.generer()

    @Test
    fun `Kan lagre og hente dødsfallshendelser`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo

            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id = UUID.randomUUID()
            repo.lagre(hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))

            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
            )
            hentMetadata(id, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `Kan lagre og hente utflytting fra norge`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    hendelseId = hendelseId,
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))

            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
            )
            hentMetadata(id, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `Kan lagre og hente sivilstand`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Sivilstand(
                    type = SivilstandTyper.GIFT,
                    gyldigFraOgMed = fixedLocalDate.minusDays(1),
                    relatertVedSivilstand = Fnr.generer(),
                    bekreftelsesdato = fixedLocalDate.plusDays(1),
                ),
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))
            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
            )
            hentMetadata(id, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `Kan lagre og hente bostedsadresse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Bostedsadresse,
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))
            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
            )
            hentMetadata(id, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `Kan lagre og hente kontaktadresse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Kontaktadresse,
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))
            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
            )
            hentMetadata(id, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `lagring av duplikate hendelser ignoreres`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.personhendelseRepo as PersonhendelsePostgresRepo
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )

            val sak1 = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id1 = UUID.randomUUID()

            val sak2 = testDataHelper.persisterJournalførtSøknadMedOppgave().first
            val id2 = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id1, SakInfo(sak1.id, sak1.saksnummer, sak1.fnr, sak1.type)))
            repo.lagre(hendelse.tilknyttSak(id2, SakInfo(sak2.id, sak2.saksnummer, sak2.fnr, sak2.type)))

            repo.hent(id1) shouldBe hendelse.tilknyttSak(
                id = id1,
                SakInfo(sak1.id, sak1.saksnummer, sak1.fnr, sak1.type),
            )
            hentMetadata(id1, dataSource) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
            )
        }
    }

    @Test
    fun `Oppdatering av oppgaveId skal lagre ny verdi`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics, fixedClock)
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    hendelseId = hendelseId,
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val id = UUID.randomUUID()
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first

            val hendelseKnyttetTilSak = hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
            repo.lagre(hendelseKnyttetTilSak)
            repo.lagre(hendelseKnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            val oppdatertHendelse = repo.hent(id)
            oppdatertHendelse shouldBe hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
                .tilSendtTilOppgave(OppgaveId("oppgaveId"))
        }
    }

    @Test
    fun `Skal kun hente personhendelser uten oppgaveId`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics, fixedClock)
            val id1 = UUID.randomUUID()
            val hendelse1 = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val id2 = UUID.randomUUID()
            val hendelse2 = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    personidenter = nonEmptyListOf("aktørId", fnr.toString()),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first

            val hendelse1KnyttetTilSak = hendelse1.tilknyttSak(id1, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
            repo.lagre(hendelse1KnyttetTilSak)
            repo.lagre(hendelse2.tilknyttSak(id2, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)))

            repo.lagre(hendelse1KnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            repo.hentPersonhendelserUtenOppgave() shouldBe listOf(
                hendelse2.tilknyttSak(
                    id2,
                    SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type),
                ),
            )
        }
    }

    @Test
    fun `Skal kun hente personhendelser som ikke har feilet for mange ganger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics, fixedClock)

            val hendelseId1 = UUID.randomUUID()
            val hendelse1 = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val hendelseId2 = UUID.randomUUID()
            val hendelse2 = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    personidenter = nonEmptyListOf("aktørId", fnr.toString()),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )

            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first

            val hendelse1TilknyttetSak =
                hendelse1.tilknyttSak(hendelseId1, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
            val hendelse2TilknyttetSak =
                hendelse2.tilknyttSak(hendelseId2, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
            repo.lagre(hendelse1TilknyttetSak)
            repo.lagre(hendelse2TilknyttetSak)

            repo.inkrementerAntallFeiledeForsøk(hendelse2TilknyttetSak)
            repo.inkrementerAntallFeiledeForsøk(hendelse2TilknyttetSak)
            repo.inkrementerAntallFeiledeForsøk(hendelse2TilknyttetSak)

            repo.hentPersonhendelserUtenOppgave() shouldBe listOf(
                hendelse1TilknyttetSak,
            )
        }
    }

    @Test
    fun `Kan inkrementere antall forsøk`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(testDataHelper.sessionFactory, testDataHelper.dbMetrics, fixedClock)
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(fixedLocalDate),
                metadata = Personhendelse.Metadata(
                    hendelseId = hendelseId,
                    personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val id = UUID.randomUUID()
            val sak = testDataHelper.persisterJournalførtSøknadMedOppgave().first

            val hendelseKnyttetTilSak = hendelse.tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
            repo.lagre(hendelseKnyttetTilSak)
            repo.inkrementerAntallFeiledeForsøk(hendelseKnyttetTilSak)
            repo.inkrementerAntallFeiledeForsøk(hendelseKnyttetTilSak)

            val oppdatertHendelse = repo.hent(id)
            oppdatertHendelse shouldBe
                hendelse
                    .tilknyttSak(id, SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type))
                    .copy(
                        antallFeiledeForsøk = 2,
                    )
        }
    }

    private fun hentMetadata(id: UUID, dataSource: DataSource): PersonhendelsePostgresRepo.MetadataJson? {
        return dataSource.withSession { session ->

            """
                select metadata from personhendelse
                where id = :id
            """.trimIndent()
                .hent(
                    mapOf("id" to id),
                    session,
                ) {
                    deserialize<PersonhendelsePostgresRepo.MetadataJson>(it.string("metadata"))
                }
        }
    }
}
