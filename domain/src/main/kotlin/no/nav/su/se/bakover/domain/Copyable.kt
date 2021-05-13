package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode

interface Copyable<Args, Type> {
    fun copy(args: Args): Type
}

sealed class CopyArgs {
    sealed class Tidslinje : CopyArgs() {
        data class NyPeriode(val periode: Periode) : Tidslinje()
        object Full : Tidslinje()
    }

    data class MaksPeriode(val periode: Periode) : CopyArgs() {
        fun forOriginal(original: Periode): Periode? {
            return when {
                !(original overlapper periode) -> null
                original inneholder periode -> periode
                original starterTidligere periode -> {
                    Periode.create(periode.fraOgMed, minOf(periode.tilOgMed, original.tilOgMed))
                }
                original slutterEtter periode -> {
                    Periode.create(maxOf(periode.fraOgMed, original.fraOgMed), periode.tilOgMed)
                }
                else -> original
            }
        }
    }
}
