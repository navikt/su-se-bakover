package no.nav.su.se.bakover.web.routes.utbetaling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling

internal fun KunneIkkeKlaregjøreUtbetaling.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeKlaregjøreUtbetaling.KunneIkkeLageUtbetalingslinjer -> HttpStatusCode.InternalServerError.errorJson(
            "Feil når vi prøvde å lage utbetalingslinjer",
            "klarte_ikke_å_lage_utbetalingslinjer",
        )

        is KunneIkkeKlaregjøreUtbetaling.KunneIkkeLagre -> HttpStatusCode.InternalServerError.errorJson(
            "Kunne ikke lagre utbetaling",
            "kunne_ikke_lagre_utbetaling",
        )
    }
}
