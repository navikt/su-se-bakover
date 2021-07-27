package no.nav.su.se.bakover.service.hendelser

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.hendelser.LeesahService.Opplysningstype.DØDSFALL
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LeesahServiceImplTest {
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
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<Fnr>()) } doReturn sakMock.right()
        }
        val personMock = mock<Person> {
            on { ident } doReturn Ident(randomFnr, AktørId("aktørId"))
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn personMock.right()
        }

        val dødsfallshendelse = lagNyPdlhendelse(DØDSFALL, sakMock.fnr)

        val leesahService = LeesahServiceImpl(
            oppgaveService = oppgaveServiceMock,
            sakService = sakServiceMock,
            personService = personServiceMock,
        )

        leesahService.prosesserNyMelding(dødsfallshendelse)

        verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe randomFnr })
        verify(personServiceMock).hentPerson(argThat { it shouldBe randomFnr })

        verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe OppgaveConfig.Revurderingsbehandling(Saksnummer(2021), AktørId("aktørId")) })
    }

    private fun lagNyPdlhendelse(opplysningstype: LeesahService.Opplysningstype, personIdent: Fnr, offset: Long = 0) = PdlHendelse(
        hendelseId = UUID.randomUUID().toString(),
        gjeldendeAktørId = null,
        offset = offset,
        opplysningstype = opplysningstype.value,
        endringstype = "OPPRETTET",
        personIdenter = listOf(personIdent.toString()),
        dødsdato = null,
        fødselsdato = null,
        fødeland = null,
        utflyttingsdato = null
    )
}
