package no.nav.su.se.bakover.service.hendelser

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.hendelse.HendelseRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelseServiceTest {
    @Test
    internal fun `kan lagre pdlhendelser`() {
        val dødsfallshendelse = lagNyPdlhendelse()
        val personRepoMock = mock<PersonRepo> {
            on { hentSaksnummerForIdenter(any()) } doReturn Saksnummer(2021)
        }
        val hendelseRepoMock = mock<HendelseRepo> {
            on { lagre(any(), any()) }.then {}
        }

        val leesahService = PersonhendelseService(
            personRepoMock,
            hendelseRepoMock
        )

        leesahService.prosesserNyMelding(dødsfallshendelse)

        verify(personRepoMock).hentSaksnummerForIdenter(argThat { it shouldBe dødsfallshendelse.personidenter })
        verify(hendelseRepoMock).lagre(argThat { it shouldBe dødsfallshendelse }, argThat { it shouldBe Saksnummer(2021) })
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val dødsfallshendelse = lagNyPdlhendelse()
        val personRepoMock = mock<PersonRepo> {
            on { hentSaksnummerForIdenter(any()) } doReturn null
        }
        val hendelseRepoMock = mock<HendelseRepo> {}

        val leesahService = PersonhendelseService(
            personRepoMock,
            hendelseRepoMock
        )

        leesahService.prosesserNyMelding(dødsfallshendelse)

        verify(personRepoMock).hentSaksnummerForIdenter(argThat { it shouldBe dødsfallshendelse.personidenter })
        verifyNoMoreInteractions(hendelseRepoMock)
    }

    private fun lagNyPdlhendelse() = PdlHendelse.Ny(
        hendelseId = UUID.randomUUID().toString(),
        gjeldendeAktørId = AktørId("123456b7890000"),
        offset = 0,
        endringstype = PdlHendelse.Endringstype.OPPRETTET,
        personidenter = listOf(FnrGenerator.random().toString(), "123456789010"),
        hendelse = PdlHendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now()),
    )
}
