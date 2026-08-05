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
            gyldigConfig(meta = OppgaveV2Data.Meta(kommentar = "x"))
        }
    }

    @Test
    fun `arkivreferanse må inneholde minst saksnr eller journalpostId`() {
        assertThrows<IllegalArgumentException> {
            gyldigConfig(
                arkivreferanse = OppgaveV2Data.Arkivreferanse(),
            )
        }
    }

    private fun gyldigConfig(
        beskrivelse: String = "Gyldig beskrivelse",
        bruker: OppgaveV2Data.Bruker? = OppgaveV2Data.Bruker(
            ident = "11111111111",
            type = OppgaveV2Data.Bruker.Type.PERSON,
        ),
        arkivreferanse: OppgaveV2Data.Arkivreferanse? = OppgaveV2Data.Arkivreferanse(
            journalpostId = "123",
        ),
        meta: OppgaveV2Data.Meta? = null,
    ) = OppgaveV2Data(
        beskrivelse = beskrivelse,
        kategorisering = OppgaveV2Data.Kategorisering(
            tema = OppgaveV2Data.Kode("SUP"),
            oppgavetype = OppgaveV2Data.Kode("BEH_SAK"),
        ),
        bruker = bruker,
        arkivreferanse = arkivreferanse,
        meta = meta,
    )
}
