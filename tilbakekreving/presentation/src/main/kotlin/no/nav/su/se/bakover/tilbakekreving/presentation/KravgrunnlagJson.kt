package no.nav.su.se.bakover.tilbakekreving.presentation

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.tilbakekreving.domain.Kravgrunnlag

data class KravgrunnlagJson(
    val id: String,
) {

    companion object {
        fun Kravgrunnlag.toJson(): String {
            return serialize(KravgrunnlagJson(id = this.kravgrunnlagId))
        }
    }
}
