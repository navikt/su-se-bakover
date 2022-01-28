package no.nav.su.se.bakover.web.routes

import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson

internal fun Avkortingsvarsel.toJson(): SimuleringJson? {
    return when (this) {
        Avkortingsvarsel.Ingen -> {
            null
        }
        is Avkortingsvarsel.Utenlandsopphold.Annullert -> {
            simulering.toJson()
        }
        is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
            simulering.toJson()
        }
        is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
            simulering.toJson()
        }
        is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
            simulering.toJson()
        }
    }
}
