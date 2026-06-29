package no.nav.su.se.bakover.service.oppgave

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Data
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nyPersonhendelseKnyttetTilSak
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal class OppgaveV2ServiceImplTest {

    @Test
    fun `bruker Oslo-dato for aktivDato i oppgave v2`() {
        val captor = argumentCaptor<OppgaveV2Data>()
        val client = mock<OppgaveV2Client> {
            on { opprettOppgaveMedSystembruker(captor.capture(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val actual = OppgaveV2ServiceImpl(client).opprettOppgaveMedSystembruker(
            saksnummer = saksnummer,
            fnr = fnr,
            sakstype = Sakstype.UFØRE,
            personhendelser = listOf(dødsfall()),
            clock = Clock.fixed(Instant.parse("2026-06-27T22:30:00Z"), ZoneOffset.UTC),
        )

        actual shouldBe nyOppgaveHttpKallResponse().right()
        captor.firstValue.aktivDato shouldBe LocalDate.parse("2026-06-28")
        captor.firstValue.fristDato shouldBe LocalDate.parse("2026-07-05")
        verify(client).opprettOppgaveMedSystembruker(any(), any())
    }

    private fun dødsfall(): Personhendelse.TilknyttetSak.IkkeSendtTilOppgave {
        return nyPersonhendelseKnyttetTilSak(
            hendelse = Personhendelse.Hendelse.Dødsfall(LocalDate.parse("2026-06-27")),
        )
    }
}
