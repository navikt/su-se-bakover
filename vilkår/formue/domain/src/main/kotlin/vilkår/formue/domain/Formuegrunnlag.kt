package vilkår.formue.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class Formuegrunnlag private constructor(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    val epsFormue: Verdier?,
    val søkersFormue: Verdier,
) : Grunnlag, KanPlasseresPåTidslinje<Formuegrunnlag> {

    fun harEPSFormue(): Boolean {
        return epsFormue != null
    }

    fun leggTilTomEPSFormueHvisDenMangler(): Formuegrunnlag {
        return epsFormue?.let { this } ?: copy(
            epsFormue = Verdier.create(
                verdiIkkePrimærbolig = 0,
                verdiEiendommer = 0,
                verdiKjøretøy = 0,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0,
            ),
        )
    }

    fun fjernEPSFormue(): Formuegrunnlag {
        return copy(epsFormue = null)
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Formuegrunnlag &&
            søkersFormue == other.søkersFormue &&
            epsFormue == other.epsFormue
    }

    fun oppdaterPeriode(periode: Periode): Formuegrunnlag {
        return this.copy(periode = periode)
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
            behandlingsPeriode: Periode,
        ): Either<KunneIkkeLageFormueGrunnlag, Formuegrunnlag> {
            val formuegrunnlag = Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
            )

            if (!(behandlingsPeriode.inneholder(periode))) {
                return KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
            }

            return formuegrunnlag.right()
        }

        fun fromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
        ): Formuegrunnlag {
            return Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
            )
        }
    }

    fun sumFormue(): Int = søkersFormue.sumVerdier() + (epsFormue?.sumVerdier() ?: 0)

    override fun copy(args: CopyArgs.Tidslinje): Formuegrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            this.copy(id = UUID.randomUUID())
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            this.copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }
}

sealed interface KunneIkkeLageFormueGrunnlag {
    data object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeLageFormueGrunnlag
}

sealed interface KunneIkkeLageFormueVerdier {
    data object DepositumErStørreEnnInnskudd : KunneIkkeLageFormueVerdier
    data object VerdierKanIkkeVæreNegativ : KunneIkkeLageFormueVerdier
}

/**
 * @throws IllegalStateException dersom antall elementer i listen ikke tilsvarer 1
 */
fun List<Formuegrunnlag>.firstOrThrowIfMultipleOrEmpty(): Formuegrunnlag {
    if (this.size != 1) {
        throw IllegalStateException("Antall elementer i listen tilsvarer ikke 1")
    }
    return this.first()
}

fun List<Formuegrunnlag>.perioderMedEPS(): List<Periode> {
    return filter { it.harEPSFormue() }.map { it.periode }.minsteAntallSammenhengendePerioder()
}

fun List<Formuegrunnlag>.harOverlappende(): Boolean {
    return map { it.periode }.harOverlappende()
}
