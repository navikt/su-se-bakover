package tilbakekreving.domain.underkjent

import no.nav.su.se.bakover.common.domain.attestering.UnderkjennAttesteringsgrunn

enum class UnderkjennAttesteringsgrunnTilbakekreving : UnderkjennAttesteringsgrunn {
    IKKE_GRUNNLAG_FOR_TILBAKEKREVING,
    DOKUMENTASJON_MANGLER,
    VEDTAKSBREVET_ER_FEIL,
    ANDRE_FORHOLD,
    MANGLER_FORHÅNDSVARSEL,
    SKAL_AVKORTES,
    UTDATERT_KRAVGRUNNLAG,
    VURDERINGEN_ER_FEIL,
}
