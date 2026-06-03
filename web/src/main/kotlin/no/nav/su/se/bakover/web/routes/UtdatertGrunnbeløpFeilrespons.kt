package no.nav.su.se.bakover.web.routes

import beregning.domain.Beregning
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.regulering.harUtdatertGrunnbeløp
import satser.domain.SatsFactory

/**
 * Returnerer et feilresultat dersom beregningen er gjort med et utdatert grunnbeløp/garantipensjon
 * (se [harUtdatertGrunnbeløp]), ellers null.
 *
 * Sjekken gjøres kun for forespørsler som kommer via HTTP-routene, slik at saksbehandlere ikke kan iverksette
 * eller sende til attestering en behandling som er beregnet før en regulering ble innført i systemet.
 */
internal fun Beregning?.utdatertGrunnbeløpFeilEllerNull(satsFactory: SatsFactory): Resultat? =
    if (this != null && this.harUtdatertGrunnbeløp(satsFactory)) {
        HttpStatusCode.BadRequest.errorJson(
            message = "Beregningen er gjort med et utdatert grunnbeløp. Beregn behandlingen på nytt før iverksetting eller attestering.",
            code = "grunnbeløp_er_utdatert",
        )
    } else {
        null
    }
