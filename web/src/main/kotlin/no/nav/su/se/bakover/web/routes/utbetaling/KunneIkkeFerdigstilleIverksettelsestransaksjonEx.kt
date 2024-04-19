package no.nav.su.se.bakover.web.routes.utbetaling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.web.routes.tilResultat

internal fun KunneIkkeFerdigstilleIverksettelsestransaksjon.tilResultat() = when (this) {
    is KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil -> HttpStatusCode.InternalServerError.errorJson(
        "Ukjent feil når vi prøvde utbetale. Kan være en av sideeffektene.",
        "utbetaling_ukjent_feil",
    )
    is KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeKlargjøreUtbetaling -> this.underliggende.tilResultat()
    is KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeLeggeUtbetalingPåKø -> this.utbetalingFeilet.tilResultat()
}
