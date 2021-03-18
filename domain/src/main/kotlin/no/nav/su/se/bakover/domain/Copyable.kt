package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode

interface Copyable<Args, Type> {
    fun copy(args: Args): Type
}

sealed class CopyArgs {
    sealed class Tidslinje {
        data class NyPeriode(val periode: Periode) : Tidslinje()
        object Full : Tidslinje()
    }
}
