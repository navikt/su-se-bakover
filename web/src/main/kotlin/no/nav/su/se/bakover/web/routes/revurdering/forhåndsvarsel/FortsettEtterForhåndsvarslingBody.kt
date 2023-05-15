package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

data class FortsettEtterForhåndsvarslingBody(
    /** Dette er begrunnelsen til beslutningen om man skal fortsette med samme eller andre opplysninger */
    val begrunnelse: String,
    /** @see BeslutningEtterForhåndsvarsling . Må ikke sammenlignes med brevvalg. */
    val valg: BeslutningEtterForhåndsvarsling,
    /** Må være utfylt dersom man skal fortsette med samme opplysninger. Skal ikke være utfylt dersom man skal fortsette med andre opplysninger */
    val fritekstTilBrev: String?,
) {
    enum class BeslutningEtterForhåndsvarsling {
        FORTSETT_MED_SAMME_OPPLYSNINGER,
        FORTSETT_MED_ANDRE_OPPLYSNINGER,
    }
}
