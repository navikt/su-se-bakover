package no.nav.su.se.bakover.service.hendelser

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelseServiceTest {

    @Test
    internal fun `kan lagre personhendelser`() {
        val personRepoMock = mock<PersonRepo> {
            on { hentSaksnummerForIdenter(any()) } doReturn Saksnummer(2021)
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val personhendelseService = PersonhendelseService(
            personRepo = personRepoMock,
            personhendelseRepo = personhendelseRepoMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(personRepoMock).hentSaksnummerForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verify(personhendelseRepoMock).lagre(
            argThat { it shouldBe nyPersonhendelse },
            argThat { it shouldBe Saksnummer(2021) },
        )
        verifyNoMoreInteractions(personhendelseRepoMock, personRepoMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val personRepoMock = mock<PersonRepo> {
            on { hentSaksnummerForIdenter(any()) } doReturn null
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()

        val personhendelseService = PersonhendelseService(
            personRepo = personRepoMock,
            personhendelseRepo = personhendelseRepoMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(personRepoMock).hentSaksnummerForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verifyNoMoreInteractions(personhendelseRepoMock, personRepoMock)
    }

    private fun lagNyPersonhendelse() = Personhendelse.Ny(
        hendelseId = UUID.randomUUID().toString(),
        gjeldendeAktørId = AktørId("123456b7890000"),
        offset = 0,
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        personidenter = listOf(FnrGenerator.random().toString(), "123456789010"),
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now()),
    )
}
