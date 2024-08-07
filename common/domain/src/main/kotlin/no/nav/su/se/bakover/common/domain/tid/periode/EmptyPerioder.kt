package no.nav.su.se.bakover.common.domain.tid.periode

import no.nav.su.se.bakover.common.tid.periode.Periode

data object EmptyPerioder : List<Periode> by emptyList(), SlåttSammenIkkeOverlappendePerioder {
    override val perioder: List<Periode> = emptyList()
    override val fraOgMed = null
    override val tilOgMed = null

    override fun toString() = "EmptyPerioder"
}
