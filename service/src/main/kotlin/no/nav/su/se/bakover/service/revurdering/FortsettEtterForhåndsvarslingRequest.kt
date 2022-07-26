package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import java.util.UUID
import kotlin.reflect.KClass

sealed interface FortsettEtterForhåndsvarslingRequest {
    val revurderingId: UUID
    val saksbehandler: NavIdentBruker.Saksbehandler
    val begrunnelse: String

    data class FortsettMedSammeOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
        val fritekstTilBrev: String,
    ) : FortsettEtterForhåndsvarslingRequest

    data class FortsettMedAndreOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
    ) : FortsettEtterForhåndsvarslingRequest
}

sealed interface FortsettEtterForhåndsvarselFeil {
    object FantIkkeRevurdering : FortsettEtterForhåndsvarselFeil
    object MåVæreEnSimulertRevurdering : FortsettEtterForhåndsvarselFeil
    data class UgyldigTilstandsovergang(
        val fra: KClass<out Forhåndsvarsel>,
        val til: KClass<out Forhåndsvarsel>,
    ) : FortsettEtterForhåndsvarselFeil

    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : FortsettEtterForhåndsvarselFeil
}
