package no.nav.su.se.bakover.database.hendelse

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.domain.sak.SakIdOgNummer
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelsePostgresRepoTest {
    private val testDataHelper = TestDataHelper()
    private val hendelsePostgresRepo = testDataHelper.hendelsePostgresRepo

    private val hendelseId = UUID.randomUUID().toString()
    private val aktørId = "abcdefghjiklm"
    private val fnr = Fnr.generer()

    @Test
    fun `Kan lagre og hente dødsfallshendelser`() {
        withMigratedDb {
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
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
            hendelsePostgresRepo.lagre(hendelse.tilknyttSak(id, SakIdOgNummer(sak.id, sak.saksnummer)))

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdOgNummer(sak.id, sak.saksnummer)
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
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
        withMigratedDb {
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()),
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

            hendelsePostgresRepo.lagre(hendelse.tilknyttSak(id, SakIdOgNummer(sak.id, sak.saksnummer)))

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdOgNummer(sak.id, sak.saksnummer)
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
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
        withMigratedDb {
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Sivilstand(
                    type = SivilstandTyper.GIFT,
                    gyldigFraOgMed = LocalDate.now().minusDays(1),
                    relatertVedSivilstand = Fnr.generer(),
                    bekreftelsesdato = LocalDate.now().plusDays(1),
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

            hendelsePostgresRepo.lagre(hendelse.tilknyttSak(id, SakIdOgNummer(sak.id, sak.saksnummer)))
            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                SakIdOgNummer(sak.id, sak.saksnummer)
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
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
        withMigratedDb {
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
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

            hendelsePostgresRepo.lagre(hendelse.tilknyttSak(id1, SakIdOgNummer(sak1.id, sak1.saksnummer)))
            hendelsePostgresRepo.lagre(hendelse.tilknyttSak(id2, SakIdOgNummer(sak2.id, sak2.saksnummer)))

            hendelsePostgresRepo.hent(id1) shouldBe hendelse.tilknyttSak(
                id = id1,
                SakIdOgNummer(sak1.id, sak1.saksnummer)
            )
            hentMetadata(id1) shouldBe PersonhendelsePostgresRepo.MetadataJson(
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
        withMigratedDb {
            val hendelse = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
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

            val hendelseKnyttetTilSak = hendelse.tilknyttSak(id, SakIdOgNummer(sak.id, sak.saksnummer))
            hendelsePostgresRepo.lagre(hendelseKnyttetTilSak)
            hendelsePostgresRepo.lagre(hendelseKnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            val oppdatertHendelse = hendelsePostgresRepo.hent(id)
            oppdatertHendelse shouldBe hendelse.tilknyttSak(id, SakIdOgNummer(sak.id, sak.saksnummer)).tilSendtTilOppgave(OppgaveId("oppgaveId"))
        }
    }

    @Test
    fun `Skal kun hente personhendelser uten oppgaveId`() {
        withMigratedDb {
            val id1 = UUID.randomUUID()
            val hendelse1 = Personhendelse.IkkeTilknyttetSak(
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
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
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
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

            val hendelse1KnyttetTilSak = hendelse1.tilknyttSak(id1, SakIdOgNummer(sak.id, sak.saksnummer))
            hendelsePostgresRepo.lagre(hendelse1KnyttetTilSak)
            hendelsePostgresRepo.lagre(hendelse2.tilknyttSak(id2, SakIdOgNummer(sak.id, sak.saksnummer)))

            hendelsePostgresRepo.lagre(hendelse1KnyttetTilSak.tilSendtTilOppgave(OppgaveId("oppgaveId")))

            hendelsePostgresRepo.hentPersonhendelserUtenOppgave() shouldBe listOf(hendelse2.tilknyttSak(id2, SakIdOgNummer(sak.id, sak.saksnummer)))
        }
    }

    private fun hentMetadata(id: UUID): PersonhendelsePostgresRepo.MetadataJson? {
        return testDataHelper.datasource.withSession { session ->
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
