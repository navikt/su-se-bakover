package vilkår.pensjon.domain

import vilkår.common.domain.Vurdering

data class Pensjonsopplysninger(
    val søktPensjonFolketrygd: SøktPensjonFolketrygd,
    val søktAndreNorskePensjoner: SøktAndreNorskePensjoner,
    val søktUtenlandskePensjoner: SøktUtenlandskePensjoner,
) {
    fun resultat(): Vurdering {
        return when {
            setOf(
                søktPensjonFolketrygd.resultat(),
                søktAndreNorskePensjoner.resultat(),
                søktUtenlandskePensjoner.resultat(),
            ) == setOf(Vurdering.Innvilget) -> {
                Vurdering.Innvilget
            }
            else -> {
                Vurdering.Avslag
            }
        }
    }

    data class SøktPensjonFolketrygd(
        val svar: Svar,
    ) {
        fun resultat(): Vurdering {
            return when (svar) {
                Svar.HarIkkeSøktPensjonFraFolketrygden -> {
                    Vurdering.Avslag
                }
                Svar.HarSøktPensjonFraFolketrygden -> {
                    Vurdering.Innvilget
                }
            }
        }

        sealed interface Svar {
            data object HarSøktPensjonFraFolketrygden : Svar
            data object HarIkkeSøktPensjonFraFolketrygden : Svar
        }
    }

    data class SøktAndreNorskePensjoner(
        val svar: Svar,
    ) {
        fun resultat(): Vurdering {
            return when (svar) {
                Svar.IkkeAktuelt -> {
                    Vurdering.Innvilget
                }
                Svar.HarSøktAndreNorskePensjonerEnnFolketrygden -> {
                    Vurdering.Innvilget
                }
                Svar.HarIkkeSøktAndreNorskePensjonerEnnFolketrygden -> {
                    Vurdering.Avslag
                }
            }
        }

        sealed interface Svar {
            data object HarSøktAndreNorskePensjonerEnnFolketrygden : Svar
            data object HarIkkeSøktAndreNorskePensjonerEnnFolketrygden : Svar
            data object IkkeAktuelt : Svar
        }
    }

    data class SøktUtenlandskePensjoner(
        val svar: Svar,
    ) {
        fun resultat(): Vurdering {
            return when (svar) {
                Svar.IkkeAktuelt -> {
                    Vurdering.Innvilget
                }
                Svar.HarSøktUtenlandskePensjoner -> {
                    Vurdering.Innvilget
                }
                Svar.HarIkkeSøktUtenlandskePensjoner -> {
                    Vurdering.Avslag
                }
            }
        }

        sealed interface Svar {
            data object HarSøktUtenlandskePensjoner : Svar
            data object HarIkkeSøktUtenlandskePensjoner : Svar
            data object IkkeAktuelt : Svar
        }
    }
}
