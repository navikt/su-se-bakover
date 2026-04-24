package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Client
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Month
import java.util.UUID

internal class MiljøstyrtFradragssjekkOppgaveoppretterTest {

    @Test
    fun `bruker v1 klient for fradragssjekk utenfor dev`() {
        val config = fradragssjekkConfig()
        val expectedResponse = nyOppgaveHttpKallResponse().right()
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgaveMedSystembruker(config) } doReturn expectedResponse
        }
        val oppgaveV2Client = mock<OppgaveV2Client>()

        val actual = MiljøstyrtFradragssjekkOppgaveoppretter(
            oppgaveService = oppgaveService,
            oppgaveV2Client = oppgaveV2Client,
            brukOppgaveV2 = false,
        ).opprett(config)

        actual shouldBe expectedResponse
        verify(oppgaveService).opprettOppgaveMedSystembruker(config)
        verifyNoInteractions(oppgaveV2Client)
    }

    @Test
    fun `bruker v2 klient for fradragssjekk i dev med v1-beskrivelse og deterministisk idempotency-key`() {
        val config = fradragssjekkConfig()
        val expectedResponse = nyOppgaveHttpKallResponse().right()
        val configCaptor = argumentCaptor<OppgaveV2Config>()
        val idempotencyKeyCaptor = argumentCaptor<UUID>()
        val oppgaveService = mock<OppgaveService>()
        val oppgaveV2Client = mock<OppgaveV2Client> {
            on { opprettOppgaveMedSystembruker(configCaptor.capture(), idempotencyKeyCaptor.capture()) } doReturn expectedResponse
        }

        val actual = MiljøstyrtFradragssjekkOppgaveoppretter(
            oppgaveService = oppgaveService,
            oppgaveV2Client = oppgaveV2Client,
            brukOppgaveV2 = true,
        ).opprett(config)

        actual shouldBe expectedResponse
        verifyNoInteractions(oppgaveService)
        verify(oppgaveV2Client).opprettOppgaveMedSystembruker(any(), any())
        configCaptor.firstValue shouldBe OppgaveV2Config(
            beskrivelse = config.beskrivelse,
            kategorisering = OppgaveV2Config.Kategorisering(
                tema = OppgaveV2Config.Kode(Tema.SUPPLERENDE_STØNAD.value),
                oppgavetype = OppgaveV2Config.Kode("VUR_KONS_YTE"),
                behandlingstema = OppgaveV2Config.Kode(Behandlingstema.SU_ALDER.toString()),
                behandlingstype = OppgaveV2Config.Kode("ae0028"),
            ),
            bruker = OppgaveV2Config.Bruker(
                ident = fnr.toString(),
                type = OppgaveV2Config.Bruker.Type.PERSON,
            ),
            aktivDato = fixedClock.instant().atZone(fixedClock.zone).toLocalDate(),
            fristDato = fixedClock.instant().atZone(fixedClock.zone).toLocalDate().plusDays(7),
            prioritet = OppgaveV2Config.Prioritet.NORMAL,
            tilknyttetSystem = null,
        )
        idempotencyKeyCaptor.firstValue shouldBe config.toOppgaveV2IdempotencyKey()
    }

    @Test
    fun `fradragssjekk gir samme idempotency-key for samme sak og måned selv med ulike avvik`() {
        val første = fradragssjekkConfig()
        val andre = første.copy(
            avvik = listOf(
                OppgaveConfig.Fradragssjekk.Avvik(
                    kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRITEKST,
                    tekst = "Helt annet avvik",
                ),
            ),
        )

        første.toOppgaveV2IdempotencyKey() shouldBe andre.toOppgaveV2IdempotencyKey()
    }

    private fun fradragssjekkConfig() = OppgaveConfig.Fradragssjekk(
        saksnummer = no.nav.su.se.bakover.common.domain.Saksnummer(12345),
        måned = Måned.fra(2021, Month.JANUARY),
        avvik = listOf(
            OppgaveConfig.Fradragssjekk.Avvik(
                kode = OppgaveConfig.Fradragssjekk.AvvikKode.FRADRAG_DIFF_OVER_10_PROSENT,
                tekst = "Første avvik",
            ),
            OppgaveConfig.Fradragssjekk.Avvik(
                kode = OppgaveConfig.Fradragssjekk.AvvikKode.ULIKT_BELOP,
                tekst = "Andre avvik",
            ),
        ),
        sakstype = Sakstype.ALDER,
        fnr = fnr,
        clock = fixedClock,
    )
}
