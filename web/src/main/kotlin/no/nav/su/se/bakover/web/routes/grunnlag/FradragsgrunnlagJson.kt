package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson.Companion.toJson
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag

internal fun Fradragsgrunnlag.toJson() = fradrag.toJson()
