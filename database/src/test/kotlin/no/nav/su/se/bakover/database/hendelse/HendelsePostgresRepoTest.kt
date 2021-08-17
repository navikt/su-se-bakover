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

            hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
            val lagretHendelse = hendelsePostgresRepo.hent(hendelseId)

            lagretHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021))
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

            hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
            val lagretHendelse = hendelsePostgresRepo.hent(hendelseId)

            lagretHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021))
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

    @Test
    fun `lagring av hendelser med samma hendelseId ignoreres`() {
        withMigratedDb {
            val hendelse1 = Personhendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = Personhendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.now()),
            )
            val hendelse2 = hendelse1.copy(hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()))

            hendelsePostgresRepo.lagre(hendelse1, Saksnummer(2021))
            hendelsePostgresRepo.lagre(hendelse2, Saksnummer(2022))

            val oppdatertHendelse = hendelsePostgresRepo.hent(hendelseId)
            oppdatertHendelse shouldBe hendelse1.tilPersistert(Saksnummer(2021))
        }
    }

    private fun Personhendelse.Ny.tilPersistert(saksnummer: Saksnummer, oppgaveId: OppgaveId? = null) =
        Personhendelse.Persistert(
            hendelseId = hendelseId,
            gjeldendeAktørId = gjeldendeAktørId,
            endringstype = endringstype,
            hendelse = hendelse,
            saksnummer = saksnummer,
            oppgaveId = oppgaveId,
        )
}
