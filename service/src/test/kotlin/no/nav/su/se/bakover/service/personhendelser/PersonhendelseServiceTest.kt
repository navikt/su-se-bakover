package no.nav.su.se.bakover.service.personhendelser

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.hendelse.PersonhendelseRepo
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
        gjeldendeAktørId = AktørId("123456b7890000"),
        endringstype = Personhendelse.Endringstype.OPPRETTET,
        personidenter = nonEmptyListOf(Fnr.generer().toString(), "123456789010"),
        hendelse = Personhendelse.Hendelse.Dødsfall(dødsdato = LocalDate.now()),
        metadata = Personhendelse.Metadata(
            hendelseId = UUID.randomUUID().toString(),
            tidligereHendelseId = null,
            offset = 0,
            partisjon = 0,
            master = "FREG",
            key = "someKey",
        ),
    )
}
