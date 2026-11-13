package no.nav.su.se.bakover.service.oppgave

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstype
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Data
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.nyPersonhendelseKnyttetTilSak
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class OppgaveV2ServiceImplTest {

    private val config = OppgaveV2Data(
        beskrivelse = "Oppgave til behandling",
        kategorisering = OppgaveV2Data.Kategorisering(
            tema = OppgaveV2Data.Kode(Tema.SUPPLERENDE_STØNAD.value),
            oppgavetype = OppgaveV2Data.Kode(Oppgavetype.BEHANDLE_SAK.value),
        ),
        arkivreferanse = OppgaveV2Data.Arkivreferanse(saksnr = saksnummer.toString()),
    )
    private val representertEnhetsnr = "4815"
    private val idempotencyKey = UUID.randomUUID()

    @Test
    fun `opprettOppgave videresender parametere og returnerer klientens resultat`() {
        val forventetResultat = nyOppgaveHttpKallResponse().right()
        val client = mock<OppgaveV2Client> {
            on { opprettOppgave(config, representertEnhetsnr, idempotencyKey) } doReturn forventetResultat
        }

        val actual = OppgaveV2ServiceImpl(client).opprettOppgave(
            config = config,
            representertEnhetsnr = representertEnhetsnr,
            idempotencyKey = idempotencyKey,
        )

        actual shouldBe forventetResultat
        verify(client).opprettOppgave(config, representertEnhetsnr, idempotencyKey)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `opprettOppgave returnerer klientens feil`() {
        val forventetResultat = KunneIkkeOppretteOppgave.left()
        val client = mock<OppgaveV2Client> {
            on { opprettOppgave(config, representertEnhetsnr, idempotencyKey) } doReturn forventetResultat
        }

        val actual = OppgaveV2ServiceImpl(client).opprettOppgave(
            config = config,
            representertEnhetsnr = representertEnhetsnr,
            idempotencyKey = idempotencyKey,
        )

        actual shouldBe forventetResultat
        verify(client).opprettOppgave(config, representertEnhetsnr, idempotencyKey)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `oppretter EPS67-oppgave med brukertoken og bevarer oppgavefeltene i V2`() {
        val bursdagEps = LocalDate.parse("2026-09-01")
        val journalpostId = JournalpostId("journalpostId")
        val idempotencyKey = UUID.randomUUID()
        val enhetsnr = "4815"
        val response = nyOppgaveHttpKallResponse()
        val client = mock<OppgaveV2Client> {
            on { opprettOppgave(any(), any(), any()) } doReturn response.right()
        }

        val config = OppgaveV2Data.påminnelseOmEPSFyller67År(
            saksnummer = saksnummer,
            bursdagEps = bursdagEps,
            fnr = fnr,
            clock = Clock.fixed(Instant.parse("2026-06-27T22:30:00Z"), ZoneOffset.UTC),
            sakstype = Sakstype.ALDER,
            journalpostId = journalpostId,
        )
        val actual = OppgaveV2ServiceImpl(client).opprettOppgave(
            config = config,
            representertEnhetsnr = requireNotNull(config.fordeling).enhet.nr,
            idempotencyKey = idempotencyKey,
        )

        actual shouldBe response.right()
        verify(client).opprettOppgave(
            config = OppgaveV2Data(
                beskrivelse = " Saksnummer : $saksnummer" +
                    "\nBrukerens ektefelle eller samboer fyller 67 år den $bursdagEps. Vurder om brukerens og evenftuelt ektefellens eller samboerens sak må revurderes.",
                kategorisering = OppgaveV2Data.Kategorisering(
                    tema = OppgaveV2Data.Kode(Tema.SUPPLERENDE_STØNAD.value),
                    oppgavetype = OppgaveV2Data.Kode(Oppgavetype.FREMLEGGING.value),
                    behandlingstema = OppgaveV2Data.Kode(Behandlingstema.SU_ALDER.value),
                    behandlingstype = OppgaveV2Data.Kode(Behandlingstype.REVURDERING.value),
                ),
                bruker = OppgaveV2Data.Bruker(fnr.toString(), OppgaveV2Data.Bruker.Type.PERSON),
                aktivDato = LocalDate.parse("2026-07-03"),
                fristDato = LocalDate.parse("2026-08-02"),
                prioritet = OppgaveV2Data.Prioritet.NORMAL,
                fordeling = OppgaveV2Data.Fordeling(enhet = OppgaveV2Data.Fordeling.Enhet(enhetsnr)),
                arkivreferanse = OppgaveV2Data.Arkivreferanse(
                    saksnr = saksnummer.toString(),
                    journalpostId = journalpostId.toString(),
                ),
            ),
            representertEnhetsnr = enhetsnr,
            idempotencyKey = idempotencyKey,
        )
        verifyNoMoreInteractions(client)
    }

    @ParameterizedTest
    @CsvSource(
        "2021-01-20, 2021-01-01, 2021-01-01",
        "2021-02-15, 2021-01-01, 2021-01-16",
    )
    fun `EPS67-datoene settes tidligst til opprettelsesdagen`(
        bursdag: String,
        forventetAktivDato: String,
        forventetFrist: String,
    ) {
        val captor = argumentCaptor<OppgaveV2Data>()
        val idempotencyKey = UUID.randomUUID()
        val client = mock<OppgaveV2Client> {
            on { opprettOppgave(any(), any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val config = OppgaveV2Data.påminnelseOmEPSFyller67År(
            saksnummer = saksnummer,
            bursdagEps = LocalDate.parse(bursdag),
            fnr = fnr,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
            journalpostId = null,
        )
        OppgaveV2ServiceImpl(client).opprettOppgave(
            config = config,
            representertEnhetsnr = requireNotNull(config.fordeling).enhet.nr,
            idempotencyKey = idempotencyKey,
        )

        verify(client).opprettOppgave(captor.capture(), eq("4815"), eq(idempotencyKey))
        captor.firstValue.aktivDato shouldBe LocalDate.parse(forventetAktivDato)
        captor.firstValue.fristDato shouldBe LocalDate.parse(forventetFrist)
    }

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
