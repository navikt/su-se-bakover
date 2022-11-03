package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import java.util.UUID

data class Pensjonsgrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val pensjonsopplysninger: Pensjonsopplysninger,
) : Grunnlag(), KanPlasseresPåTidslinje<Pensjonsgrunnlag> {

    fun oppdaterPeriode(periode: Periode): Pensjonsgrunnlag {
        return copy(periode = periode)
    }

    fun tilResultat(): Vurdering {
        return pensjonsopplysninger.resultat()
    }

    override fun copy(args: CopyArgs.Tidslinje): Pensjonsgrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Pensjonsgrunnlag &&
            other.pensjonsopplysninger == pensjonsopplysninger
    }
}

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

        sealed class Svar {
            object HarSøktPensjonFraFolketrygden : Svar()
            object HarIkkeSøktPensjonFraFolketrygden : Svar()
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

        sealed class Svar {
            object HarSøktAndreNorskePensjonerEnnFolketrygden : Svar()
            object HarIkkeSøktAndreNorskePensjonerEnnFolketrygden : Svar()
            object IkkeAktuelt : Svar()
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

        sealed class Svar {
            object HarSøktUtenlandskePensjoner : Svar()
            object HarIkkeSøktUtenlandskePensjoner : Svar()
            object IkkeAktuelt : Svar()
        }
    }
}
