package no.nav.su.se.bakover.domain.revurdering

import com.fasterxml.jackson.annotation.JsonCreator

data class InformasjonSomRevurderes @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    val informasjonSomRevurderes: Map<Revurderingsteg, Vurderingstatus>,
) : Map<Revurderingsteg, Vurderingstatus> by informasjonSomRevurderes {

    fun vurdert(revurderingsteg: Revurderingsteg): InformasjonSomRevurderes {
        return copy(
            informasjonSomRevurderes = informasjonSomRevurderes.toMutableMap().apply {
                put(revurderingsteg, Vurderingstatus.Vurdert)
            },
        )
    }
}
