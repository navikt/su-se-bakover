package no.nav.su.se.bakover.database.hendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
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
            val hendelse = PdlHendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = PdlHendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = PdlHendelse.Hendelse.Dødsfall(LocalDate.now()),
            )

            hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
            val lagretHendelse = hendelsePostgresRepo.hent(hendelseId)

            lagretHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021))
        }
    }

    @Test
    fun `Kan lagre og hente utflytting fra norge`() {
        withMigratedDb {
            val hendelse = PdlHendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = PdlHendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = PdlHendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()),
            )

            hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
            val lagretHendelse = hendelsePostgresRepo.hent(hendelseId)

            lagretHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021))
        }
    }

    @Test
    fun `Oppdatering av oppgaveId skal lagre ny verdi`() {
        withMigratedDb {
            val hendelse = PdlHendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = PdlHendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = PdlHendelse.Hendelse.Dødsfall(LocalDate.now()),
            )

            hendelsePostgresRepo.lagre(hendelse, Saksnummer(2021))
            hendelsePostgresRepo.oppdaterOppgave(hendelseId, OppgaveId("oppgaveId"))

            val oppdatertHendelse = hendelsePostgresRepo.hent(hendelseId)
            oppdatertHendelse shouldBe hendelse.tilPersistert(Saksnummer(2021), OppgaveId("oppgaveId"))
        }
    }

    @Test
    fun `lagring av hendelser med samma hendelseId ignoreres`() {
        withMigratedDb {
            val hendelse1 = PdlHendelse.Ny(
                hendelseId = hendelseId,
                gjeldendeAktørId = AktørId(aktørId),
                endringstype = PdlHendelse.Endringstype.OPPRETTET,
                offset = 0,
                personidenter = listOf(aktørId, fnr.toString()),
                hendelse = PdlHendelse.Hendelse.Dødsfall(LocalDate.now()),
            )
            val hendelse2 = hendelse1.copy(hendelse = PdlHendelse.Hendelse.UtflyttingFraNorge(LocalDate.now()))

            hendelsePostgresRepo.lagre(hendelse1, Saksnummer(2021))
            hendelsePostgresRepo.lagre(hendelse2, Saksnummer(2022))

            val oppdatertHendelse = hendelsePostgresRepo.hent(hendelseId)
            oppdatertHendelse shouldBe hendelse1.tilPersistert(Saksnummer(2021))
        }
    }

    private fun PdlHendelse.Ny.tilPersistert(saksnummer: Saksnummer, oppgaveId: OppgaveId? = null) = PdlHendelse.Persistert(
        hendelseId = hendelseId,
        gjeldendeAktørId = gjeldendeAktørId,
        endringstype = endringstype,
        hendelse = hendelse,
        saksnummer = saksnummer,
        oppgaveId = oppgaveId
    )
}
