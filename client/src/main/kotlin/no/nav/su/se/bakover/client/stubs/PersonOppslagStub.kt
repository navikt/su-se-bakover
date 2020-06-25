package no.nav.su.se.bakover.client.stubs

import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr

object PersonOppslagStub :
        PersonOppslag {
    override fun person(ident: Fnr): ClientResponse = ClientResponse(200,
            //language=JSON
            """
        {
                "aktørid": "aktørid",
                "fnr": "$ident",
                "fornavn": "Tore",
                "mellomnavn": "Johnas",
                "etternavn": "Strømøy",
                "telefonnummer": "49494949",
                "gateadresse": "Veiveien 2",
                "postnummer": 1337,
                "poststed": "Sandvika",
                "bruksenhet": "en bruksenhet",
                "bokommune": "Sandvika",
                "statsborgerskap": "Svorsk"
        }
      """.trimIndent()
    )

    override fun aktørId(ident: Fnr): String = "aktørid"
}
