package no.nav.su.se.bakover.database.hendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class HendelsePostgresRepoTest {
    private val testDataHelper = TestDataHelper()
    private val hendelsePostgresRepo = testDataHelper.hendelsePostgresRepo

    private val hendelseId = UUID.randomUUID().toString()
    private val aktørId = "abcdefghjiklm"
    private val fnr = FnrGenerator.random()

    @Test
    fun `Kan lagre og hente dødsfallshendelser`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
            )
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()
            hendelsePostgresRepo.lagre(hendelse, id, sak.id)

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
        }
    }

    @Test
    fun `Kan lagre og hente utflytting fra norge`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()),
            )
            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()

            hendelsePostgresRepo.lagre(hendelse, id, sak.id)

            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
        }
    }

    @Test
    fun `lagring av duplikate hendelser ignoreres`() {
        withMigratedDb {
            val hendelse = Personhendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
            )

            val sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()
            val id = UUID.randomUUID()

            hendelsePostgresRepo.lagre(hendelse, id, sak.id)
            hendelsePostgresRepo.lagre(
                personhendelse = hendelse.copy(
                    // Skal ikke lagres.
                    endringstype = Personhendelse.Endringstype.ANNULLERT,
                ),
                id = id,
                sakId = sak.id,
            )
            hendelsePostgresRepo.hent(id) shouldBe hendelse.tilknyttSak(
                id = id,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
            )
        }
    }

    // TODO jah: Denne testen er litt prematur. Blir implementert i neste PR
    // @Test
    // fun `Oppdatering av oppgaveId skal lagre ny verdi`() {
    //     withMigratedDb {
    //         val hendelse = Personhendelse.Ny(
    //             hendelseId = hendelseId,
    //             gjeldendeAktørId = AktørId(aktørId),
    //             endringstype = Personhendelse.Endringstype.OPPRETTET,
    //             offset = 0,
    //             personidenter = listOf(aktørId, fnr.toString()),
    //             hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
    //         )
    //
    //         hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
    //         hendelsePostgresRepo.oppdaterOppgave(hendelseId, OppgaveId("oppgaveId"))
    //
    //         val oppdatertHendelse = hendelsePostgresRepo.hent(hendelseId)
    //         oppdatertHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021), OppgaveId("oppgaveId"))
    //     }
    // }

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
}
