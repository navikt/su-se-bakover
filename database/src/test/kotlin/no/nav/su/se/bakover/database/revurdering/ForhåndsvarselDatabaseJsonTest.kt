package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class ForhåndsvarselDatabaseJsonTest {
    @Test
    fun `ingen forhåndsvarsel json`() {
        //language=JSON
        val ingenJson = """
            {
              "type": "IngenForhåndsvarsel"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            ingenJson,
            serialize(ForhåndsvarselDatabaseJson.from(Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles)),
            true,
        )
    }

    @Test
    fun `sendt forhåndsvarsel json`() {
        //language=JSON
        val sendtJson = """
            {
              "type": "Sendt"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            sendtJson,
            serialize(
                ForhåndsvarselDatabaseJson.from(
                    Forhåndsvarsel.UnderBehandling.Sendt,
                ),
            ),
            true,
        )
    }

    @Test
    fun `besluttet fortsett med samme opplysninger json`() {
        //language=JSON
        val besluttetJson = """
            {
              "type": "Besluttet",
              "valg": "FortsettSammeOpplysninger",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            besluttetJson,
            serialize(
                ForhåndsvarselDatabaseJson.from(
                    Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
                ),
            ),
            true,
        )
    }

    @Test
    fun `besluttet fortsett med andre opplysninger json`() {
        //language=JSON
        val besluttetJson = """
            {
              "type": "Besluttet",
              "valg": "FortsettMedAndreOpplysninger",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            besluttetJson,
            serialize(
                ForhåndsvarselDatabaseJson.from(
                    Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("begrunnelse"),
                ),
            ),
            true,
        )
    }

    @Test
    fun `besluttet avsluttet json`() {
        //language=JSON
        val besluttetJson = """
            {
              "type": "Besluttet",
              "valg": "AvsluttUtenEndringer",
              "begrunnelse": "begrunnelse"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            besluttetJson,
            serialize(
                ForhåndsvarselDatabaseJson.from(
                    Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("begrunnelse"),
                ),
            ),
            true,
        )
    }
}
