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

    data class BegrensetTil(val periode: Periode) : CopyArgs() {
        fun begrensTil(original: Periode): Periode? {
            return when {
                original overlapper periode -> Periode.create(
                    fraOgMed = maxOf(periode.fraOgMed, original.fraOgMed),
                    tilOgMed = minOf(periode.tilOgMed, original.tilOgMed),
                )
                else -> null
            }
        }
    }
}
