package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

interface Fradrag : PeriodisertInformasjon {
    fun getFradragstype(): Fradragstype
    fun getMånedsbeløp(): Double
    fun getUtenlandskInntekt(): UtenlandskInntekt? // TODO can we pls do something about this one?
    fun getTilhører(): FradragTilhører
}

enum class FradragTilhører {
    BRUKER,
    EPS;
}
