package no.nav.su.se.bakover.database.hendelse

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.SivilstandTyper
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
            val hendelse = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                metadata = Personhendelse.Metadata(
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
            hendelsePostgresRepo.lagre(hendelse, id, sak.id)

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
            )
        }
    }

    @Test
    fun `Kan lagre og hente utflytting fra norge`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()),
                metadata = Personhendelse.Metadata(
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

            hendelsePostgresRepo.lagre(hendelse, id, sak.id)

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
            )
        }
    }

    @Test
    fun `Kan lagre og hente sivilstand`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Sivilstand(
                    type = SivilstandTyper.GIFT,
                    gyldigFraOgMed = LocalDate.now().minusDays(1),
                    relatertVedSivilstand = Fnr.generer(),
                    bekreftelsesdato = LocalDate.now().plusDays(1),
                ),
                metadata = Personhendelse.Metadata(
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

            hendelsePostgresRepo.lagre(hendelse, id, sak.id)

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
            hentMetadata(id) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
            )
        }
    }

    @Test
    fun `lagring av duplikate hendelser ignoreres`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                metadata = Personhendelse.Metadata(
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

            hendelsePostgresRepo.lagre(hendelse, id1, sak1.id)
            hendelsePostgresRepo.lagre(
                personhendelse = hendelse,
                id = id2,
                sakId = sak2.id,
            )
            hendelsePostgresRepo.hent(id1) shouldBe hendelse.tilknyttSak(
                id = id1,
                sakId = sak1.id,
                saksnummer = sak1.saksnummer,
            )
            hentMetadata(id1) shouldBe PersonhendelsePostgresRepo.MetadataJson(
                hendelseId = hendelseId,
                tidligereHendelseId = null,
                offset = 0,
                partisjon = 0,
                master = "FREG",
                key = "someKey",
            )
        }
    }

    @Test
    fun `Oppdatering av oppgaveId skal lagre ny verdi`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                metadata = Personhendelse.Metadata(
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val id = UUID.randomUUID()
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            hendelsePostgresRepo.lagre(hendelse, id, sak.id)
            hendelsePostgresRepo.oppdaterOppgave(id, OppgaveId("oppgaveId"))

            val oppdatertHendelse = hendelsePostgresRepo.hent(id)
            oppdatertHendelse shouldBe hendelse.tilknyttSak(id, sak.id, Saksnummer(2021), OppgaveId("oppgaveId"))
        }
    }

    @Test
    fun `Skal kun hente personhendelser uten oppgaveId`() {
        withMigratedDb {
            val id1 = UUID.randomUUID()
            val hendelse1 = Personhendelse.Ny(
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                metadata = Personhendelse.Metadata(
                    hendelseId = hendelseId,
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val id2 = UUID.randomUUID()
            val hendelse2 = Personhendelse.Ny(
                gjeldendeAktørId = AktørId("aktørId"),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                personidenter = nonEmptyListOf("aktørId", fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
                metadata = Personhendelse.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    tidligereHendelseId = null,
                    offset = 0,
                    partisjon = 0,
                    master = "FREG",
                    key = "someKey",
                ),
            )
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            hendelsePostgresRepo.lagre(hendelse1, id1, sak.id)
            hendelsePostgresRepo.lagre(hendelse2, id2, sak.id)
            hendelsePostgresRepo.oppdaterOppgave(id1, OppgaveId("oppgaveId"))

            hendelsePostgresRepo.hentPersonhendelserUtenOppgave() shouldBe listOf(hendelse2.tilknyttSak(id2, sak.id, sak.saksnummer, null))
        }
    }

    private fun Personhendelse.Ny.tilknyttSak(
        id: UUID,
        sakId: UUID,
        saksnummer: Saksnummer,
        oppgaveId: OppgaveId? = null,
    ) =
        Personhendelse.TilknyttetSak(
            id = id,
            gjeldendeAktørId = gjeldendeAktørId,
            endringstype = endringstype,
            hendelse = hendelse,
            sakId = sakId,
            saksnummer = saksnummer,
            oppgaveId = oppgaveId,
        )

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
