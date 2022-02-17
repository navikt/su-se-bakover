package no.nav.su.se.bakover.database.hendelse

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
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
            val repo = testDataHelper.hendelsePostgresRepo

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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()
            repo.lagre(hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr)))

            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr),
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
            val repo = testDataHelper.hendelsePostgresRepo
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr)))

            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr),
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
            val repo = testDataHelper.hendelsePostgresRepo
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr)))
            repo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr),
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
            val repo = testDataHelper.hendelsePostgresRepo
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

            val sak1 = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id1 = UUID.randomUUID()

            val sak2 = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id2 = UUID.randomUUID()

            repo.lagre(hendelse.tilknyttSak(id1, SakIdSaksnummerFnr(sak1.id, sak1.saksnummer, sak1.fnr)))
            repo.lagre(hendelse.tilknyttSak(id2, SakIdSaksnummerFnr(sak2.id, sak2.saksnummer, sak2.fnr)))

            repo.hent(id1) shouldBe hendelse.tilknyttSak(
                id = id1,
                SakIdSaksnummerFnr(sak1.id, sak1.saksnummer, sak1.fnr),
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
            val repo = PersonhendelsePostgresRepo(dataSource, fixedClock)
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val hendelseKnyttetTilSak = hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
            repo.lagre(hendelseKnyttetTilSak)
            repo.lagre(hendelseKnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            val oppdatertHendelse = repo.hent(id)
            oppdatertHendelse shouldBe hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
                .tilSendtTilOppgave(OppgaveId("oppgaveId"))
        }
    }

    @Test
    fun `Skal kun hente personhendelser uten oppgaveId`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(dataSource, fixedClock)
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val hendelse1KnyttetTilSak = hendelse1.tilknyttSak(id1, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
            repo.lagre(hendelse1KnyttetTilSak)
            repo.lagre(hendelse2.tilknyttSak(id2, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr)))

            repo.lagre(hendelse1KnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            repo.hentPersonhendelserUtenOppgave() shouldBe listOf(
                hendelse2.tilknyttSak(
                    id2,
                    SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr),
                ),
            )
        }
    }

    @Test
    fun `Skal kun hente personhendelser som ikke har feilet for mange ganger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = PersonhendelsePostgresRepo(dataSource, fixedClock)

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

            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val hendelse1TilknyttetSak = hendelse1.tilknyttSak(hendelseId1, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
            val hendelse2TilknyttetSak = hendelse2.tilknyttSak(hendelseId2, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
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
            val repo = PersonhendelsePostgresRepo(dataSource, fixedClock)
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
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val hendelseKnyttetTilSak = hendelse.tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
            repo.lagre(hendelseKnyttetTilSak)
            repo.inkrementerAntallFeiledeForsøk(hendelseKnyttetTilSak)
            repo.inkrementerAntallFeiledeForsøk(hendelseKnyttetTilSak)

            val oppdatertHendelse = repo.hent(id)
            oppdatertHendelse shouldBe
                hendelse
                    .tilknyttSak(id, SakIdSaksnummerFnr(sak.id, sak.saksnummer, sak.fnr))
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
