package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.vilkår.Resultat
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

    fun tilResultat(): Resultat {
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
    val folketrygd: Folketrygd,
    val andreNorske: AndreNorske,
    val utenlandske: Utenlandske,
) {
    fun resultat(): Resultat {
        return when {
            setOf(
                folketrygd.resultat(),
                andreNorske.resultat(),
                utenlandske.resultat(),
            ) == setOf(Resultat.Innvilget) -> {
                Resultat.Innvilget
            }
            else -> {
                Resultat.Avslag
            }
        }
    }

    data class Folketrygd(
        val svar: Svar,
    ) {
        init {
            require(svar !is Svar.IkkeAktuelt) { "Må være vurdert" }
        }

        fun resultat(): Resultat {
            return when (svar) {
                Svar.IkkeAktuelt -> {
                    throw IllegalStateException("Skal være ja/nei")
                }
                Svar.Ja -> {
                    Resultat.Innvilget
                }
                Svar.Nei -> {
                    Resultat.Avslag
                }
            }
        }
    }

    data class AndreNorske(
        val svar: Svar,
    ) {
        fun resultat(): Resultat {
            return when (svar) {
                Svar.IkkeAktuelt -> {
                    Resultat.Innvilget
                }
                Svar.Ja -> {
                    Resultat.Innvilget
                }
                Svar.Nei -> {
                    Resultat.Avslag
                }
            }
        }
    }

    data class Utenlandske(
        val svar: Svar,
    ) {
        fun resultat(): Resultat {
            return when (svar) {
                Svar.IkkeAktuelt -> {
                    Resultat.Innvilget
                }
                Svar.Ja -> {
                    Resultat.Innvilget
                }
                Svar.Nei -> {
                    Resultat.Avslag
                }
            }
        }
    }

    sealed class Svar {
        object Ja : Svar()
        object Nei : Svar()
        object IkkeAktuelt : Svar()
    }
}
