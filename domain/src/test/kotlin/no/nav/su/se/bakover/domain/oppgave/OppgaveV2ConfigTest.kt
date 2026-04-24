package no.nav.su.se.bakover.domain.oppgave

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class OppgaveV2ConfigTest {

    @Test
    fun `krever minst en av bruker eller arkivreferanse`() {
        assertThrows<IllegalArgumentException> {
            gyldigConfig(
                bruker = null,
                arkivreferanse = null,
            )
        }
    }

    @Test
    fun `beskrivelse kan ikke inneholde headerseparator`() {
        assertThrows<IllegalArgumentException> {
            gyldigConfig(beskrivelse = "Hei --- der")
        }
    }

    @Test
    fun `meta kommentar følger samme fritekstregler som beskrivelse`() {
        assertThrows<IllegalArgumentException> {
            gyldigConfig(meta = OppgaveV2Config.Meta(kommentar = "x"))
        }
    }

    @Test
    fun `arkivreferanse må inneholde minst saksnr eller journalpostId`() {
        assertThrows<IllegalArgumentException> {
            gyldigConfig(
                arkivreferanse = OppgaveV2Config.Arkivreferanse(),
            )
        }
    }

    private fun gyldigConfig(
        beskrivelse: String = "Gyldig beskrivelse",
        bruker: OppgaveV2Config.Bruker? = OppgaveV2Config.Bruker(
            ident = "11111111111",
            type = OppgaveV2Config.Bruker.Type.PERSON,
        ),
        arkivreferanse: OppgaveV2Config.Arkivreferanse? = OppgaveV2Config.Arkivreferanse(
            journalpostId = "123",
        ),
        meta: OppgaveV2Config.Meta? = null,
    ) = OppgaveV2Config(
        beskrivelse = beskrivelse,
        kategorisering = OppgaveV2Config.Kategorisering(
            tema = OppgaveV2Config.Kode("SUP"),
            oppgavetype = OppgaveV2Config.Kode("BEH_SAK"),
        ),
        bruker = bruker,
        arkivreferanse = arkivreferanse,
        meta = meta,
    )
}
