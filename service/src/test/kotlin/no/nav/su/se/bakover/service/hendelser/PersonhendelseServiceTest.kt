package no.nav.su.se.bakover.service.hendelser

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelseServiceTest {

    @Test
    internal fun `kan lagre personhendelser`() {
        val sakId = UUID.randomUUID()
        val sakRepoMock = mock<SakRepo> {
            on { hentSakIdForIdenter(any()) } doReturn sakId
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()
        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verify(personhendelseRepoMock).lagre(
            personhendelse = argThat { it shouldBe nyPersonhendelse },
            id = any(),
            sakId = argThat { it shouldBe sakId },
        )
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock)
    }

    @Test
    internal fun `ignorerer hendelser for personer som ikke har en sak`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSakIdForIdenter(any()) } doReturn null
        }
        val personhendelseRepoMock = mock<PersonhendelseRepo>()

        val personhendelseService = PersonhendelseService(
            sakRepo = sakRepoMock,
            personhendelseRepo = personhendelseRepoMock,
        )
        val nyPersonhendelse = lagNyPersonhendelse()
        personhendelseService.prosesserNyHendelse(nyPersonhendelse)

        verify(sakRepoMock).hentSakIdForIdenter(argThat { it shouldBe nyPersonhendelse.personidenter })
        verifyNoMoreInteractions(personhendelseRepoMock, sakRepoMock)
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
