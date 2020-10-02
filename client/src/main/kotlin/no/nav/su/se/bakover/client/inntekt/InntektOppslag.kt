package no.nav.su.se.bakover.client.inntekt

import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.domain.Fnr

interface InntektOppslag {
    fun inntekt(
        ident: Fnr,
        innloggetSaksbehandlerToken: String,
        fraOgMedDato: String,
        tilOgMedDato: String
    ): ClientResponse
}
