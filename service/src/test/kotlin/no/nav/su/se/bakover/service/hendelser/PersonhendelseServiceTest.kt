package no.nav.su.se.bakover.service.hendelser

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.hendelse.PdlHendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PersonhendelseServiceTest {
    @Test
    internal fun `lager oppgave for dødsfall`() {
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val randomFnr = FnrGenerator.random()
        val sakMock = mock<Sak>() {
            on { fnr } doReturn randomFnr
            on { saksnummer } doReturn Saksnummer(2021)
        }

        val dødsfallshendelse = lagNyPdlhendelse(sakMock.fnr)

        val leesahService = PersonhendelseService(
            mock(),
            mock()
        )

        leesahService.prosesserNyMelding(dødsfallshendelse, "json")

        verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe OppgaveConfig.Revurderingsbehandling(Saksnummer(2021), AktørId("aktørId")) })
    }

    private fun lagNyPdlhendelse(personIdent: Fnr, offset: Long = 0) = PdlHendelse.Dødsfall(
        hendelseId = UUID.randomUUID().toString(),
        gjeldendeAktørId = AktørId("123456b7890000"),
        offset = offset,
        endringstype = PdlHendelse.Endringstype.OPPRETTET,
        personidenter = listOf(personIdent.toString()),
        dødsdato = LocalDate.now(),
    )
}
