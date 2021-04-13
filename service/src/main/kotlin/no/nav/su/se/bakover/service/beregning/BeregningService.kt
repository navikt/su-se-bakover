package no.nav.su.se.bakover.service.beregning

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

class BeregningService {
    fun beregn(søknadsbehandling: Søknadsbehandling, fradrag: List<Fradrag>, begrunnelse: String?): Beregning {
        return BeregningStrategyFactory().beregn(søknadsbehandling, fradrag, begrunnelse)
    }
}
