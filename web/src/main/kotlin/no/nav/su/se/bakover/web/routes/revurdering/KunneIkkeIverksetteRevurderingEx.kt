package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.tilResultat
import no.nav.su.se.bakover.web.routes.utbetaling.tilResultat

internal fun KunneIkkeIverksetteRevurdering.tilResultat() = when (this) {
    is KunneIkkeIverksetteRevurdering.Saksfeil -> {
        when (this) {
            is KunneIkkeIverksetteRevurdering.Saksfeil.FantIkkeRevurdering -> fantIkkeRevurdering
            is KunneIkkeIverksetteRevurdering.Saksfeil.UgyldigTilstand -> ugyldigTilstand(fra, til)
            is KunneIkkeIverksetteRevurdering.Saksfeil.Revurderingsfeil -> underliggende.tilResultat()
            is KunneIkkeIverksetteRevurdering.Saksfeil.DetHarKommetNyeOverlappendeVedtak -> Feilresponser.detHarKommetNyeOverlappendeVedtak
            is KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet -> this.underliggende.tilResultat()
            is KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument -> this.feil.tilResultat()
        }
    }

    is KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet -> this.feil.tilResultat()
}
