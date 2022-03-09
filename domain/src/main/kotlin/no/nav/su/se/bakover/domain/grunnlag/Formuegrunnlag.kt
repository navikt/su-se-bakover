package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class Formuegrunnlag private constructor(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    val epsFormue: Verdier?,
    val søkersFormue: Verdier,
    val begrunnelse: String?,
) : Grunnlag(), KanPlasseresPåTidslinje<Formuegrunnlag> {

    fun harEPSFormue(): Boolean {
        return epsFormue != null
    }

    fun fjernEPSFormue(): Formuegrunnlag {
        return copy(epsFormue = null)
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Formuegrunnlag &&
            søkersFormue == other.søkersFormue &&
            epsFormue == other.epsFormue &&
            begrunnelse == other.begrunnelse
    }

    data class Verdier private constructor(
        val verdiIkkePrimærbolig: Int,
        val verdiEiendommer: Int,
        val verdiKjøretøy: Int,
        val innskudd: Int,
        val verdipapir: Int,
        val pengerSkyldt: Int,
        val kontanter: Int,
        val depositumskonto: Int,
    ) {
        internal fun sumVerdier(): Int {
            return verdiIkkePrimærbolig +
                verdiEiendommer +
                verdiKjøretøy +
                verdipapir +
                pengerSkyldt +
                kontanter +
                ((innskudd - depositumskonto).coerceAtLeast(0))
        }

        companion object {
            fun create(
                verdiIkkePrimærbolig: Int,
                verdiEiendommer: Int,
                verdiKjøretøy: Int,
                innskudd: Int,
                verdipapir: Int,
                pengerSkyldt: Int,
                kontanter: Int,
                depositumskonto: Int,
            ): Verdier = tryCreate(
                verdiIkkePrimærbolig = verdiIkkePrimærbolig,
                verdiEiendommer = verdiEiendommer,
                verdiKjøretøy = verdiKjøretøy,
                innskudd = innskudd,
                verdipapir = verdipapir,
                pengerSkyldt = pengerSkyldt,
                kontanter = kontanter,
                depositumskonto = depositumskonto,
            ).getOrHandle { throw IllegalArgumentException(it.toString()) }

            fun tryCreate(
                verdiIkkePrimærbolig: Int,
                verdiEiendommer: Int,
                verdiKjøretøy: Int,
                innskudd: Int,
                verdipapir: Int,
                pengerSkyldt: Int,
                kontanter: Int,
                depositumskonto: Int,
            ): Either<KunneIkkeLageFormueVerdier, Verdier> {
                if (depositumskonto > innskudd) {
                    return KunneIkkeLageFormueVerdier.DepositumErStørreEnnInnskudd.left()
                }

                if (
                    verdiIkkePrimærbolig < 0 ||
                    verdiEiendommer < 0 ||
                    verdiKjøretøy < 0 ||
                    innskudd < 0 ||
                    verdipapir < 0 ||
                    pengerSkyldt < 0 ||
                    kontanter < 0 ||
                    depositumskonto < 0
                ) {
                    return KunneIkkeLageFormueVerdier.VerdierKanIkkeVæreNegativ.left()
                }

                return Verdier(
                    verdiIkkePrimærbolig = verdiIkkePrimærbolig,
                    verdiEiendommer = verdiEiendommer,
                    verdiKjøretøy = verdiKjøretøy,
                    innskudd = innskudd,
                    verdipapir = verdipapir,
                    pengerSkyldt = pengerSkyldt,
                    kontanter = kontanter,
                    depositumskonto = depositumskonto,
                ).right()
            }
        }
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
            begrunnelse: String?,
            // Denne tar ikke høyde for søknadsbehandling da denne ikke nødvendigvis er fullstendig
            bosituasjon: Bosituasjon.Fullstendig,
            behandlingsPeriode: Periode,
        ): Either<KunneIkkeLageFormueGrunnlag, Formuegrunnlag> {
            val formuegrunnlag = Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
                begrunnelse = if (begrunnelse.isNullOrBlank()) null else begrunnelse,
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = listOf(bosituasjon),
                formue = listOf(formuegrunnlag),
            ).resultat.mapLeft {
                if (it.contains(Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS)) {
                    return KunneIkkeLageFormueGrunnlag.MåHaEpsHvisManHarSattEpsFormue.left()
                }
                if (it.contains(Konsistensproblem.BosituasjonOgFormue.EPSFormueperiodeErUtenforBosituasjonPeriode)) {
                    return KunneIkkeLageFormueGrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode.left()
                }
            }

            if (!(behandlingsPeriode.inneholder(periode))) {
                return KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
            }
            return formuegrunnlag.right()
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
            begrunnelse: String?,
            // Tillater ufullstending for å kunne bruke med søknadsbehandling
            bosituasjon: Bosituasjon,
            behandlingsPeriode: Periode,
        ): Either<KunneIkkeLageFormueGrunnlag, Formuegrunnlag> {
            val formuegrunnlag = Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
                begrunnelse = if (begrunnelse.isNullOrBlank()) null else begrunnelse,
            )
            SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = listOf(bosituasjon),
                formue = listOf(formuegrunnlag),
            ).resultat.mapLeft {
                if (it.contains(Konsistensproblem.BosituasjonOgFormue.IngenEPSMenFormueForEPS)) {
                    return KunneIkkeLageFormueGrunnlag.MåHaEpsHvisManHarSattEpsFormue.left()
                }
                if (it.contains(Konsistensproblem.BosituasjonOgFormue.EPSFormueperiodeErUtenforBosituasjonPeriode)) {
                    return KunneIkkeLageFormueGrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode.left()
                }
            }

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
            begrunnelse: String?,
        ): Formuegrunnlag {
            return Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
                begrunnelse = begrunnelse,
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

sealed class KunneIkkeLageFormueGrunnlag {
    object EpsFormueperiodeErUtenforBosituasjonPeriode : KunneIkkeLageFormueGrunnlag()
    object MåHaEpsHvisManHarSattEpsFormue : KunneIkkeLageFormueGrunnlag()
    object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeLageFormueGrunnlag()
}

sealed class KunneIkkeLageFormueVerdier {
    object DepositumErStørreEnnInnskudd : KunneIkkeLageFormueVerdier()
    object VerdierKanIkkeVæreNegativ : KunneIkkeLageFormueVerdier()
}

fun List<Formuegrunnlag>.harEPSFormue() = any { it.harEPSFormue() }

/**
 * @throws IllegalStateException dersom antall elementer i listen ikke tilsvarer 1
 */
fun List<Formuegrunnlag>.firstOrThrowIfMultipleOrEmpty(): Formuegrunnlag {
    if (this.size != 1) {
        throw IllegalStateException("Antall elementer i listen tilsvarer ikke 1")
    }
    return this.first()
}
