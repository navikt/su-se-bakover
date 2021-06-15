package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import org.jetbrains.annotations.TestOnly
import java.util.UUID

data class Formuegrunnlag private constructor(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    val epsFormue: Verdier?,
    val søkersFormue: Verdier,
    val begrunnelse: String?,
) : Grunnlag(), KanPlasseresPåTidslinje<Formuegrunnlag> {
    data class Verdier(
        val verdiIkkePrimærbolig: Int,
        val verdiEiendommer: Int,
        val verdiKjøretøy: Int,
        val innskudd: Int,
        val verdipapir: Int,
        val pengerSkyldt: Int,
        val kontanter: Int,
        val depositumskonto: Int,
    ) {
        init {
            require(
                verdiIkkePrimærbolig >= 0 &&
                    verdiEiendommer >= 0 &&
                    verdiKjøretøy >= 0 &&
                    innskudd >= 0 &&
                    verdipapir >= 0 &&
                    pengerSkyldt >= 0 &&
                    kontanter >= 0 &&
                    depositumskonto >= 0,
            ) {
                "Alle formueverdiene må være større eller lik 0. Var: $this"
            }
        }

        internal fun sumVerdier(): Int {
            return verdiIkkePrimærbolig +
                verdiEiendommer +
                verdiKjøretøy +
                innskudd +
                verdipapir +
                pengerSkyldt +
                kontanter +
                depositumskonto
        }

        companion object {
            @TestOnly
            fun empty() = Verdier(
                verdiIkkePrimærbolig = 0,
                verdiEiendommer = 0,
                verdiKjøretøy = 0,
                innskudd = 0,
                verdipapir = 0,
                pengerSkyldt = 0,
                kontanter = 0,
                depositumskonto = 0,
            )
        }
    }

    fun oppdaterPeriode(periode: Periode): Formuegrunnlag {
        return this.copy(periode = periode)
    }

    companion object {

        @TestOnly
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt = Tidspunkt.now(),
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
            begrunnelse: String?,
            bosituasjon: Bosituasjon.Fullstendig,
            behandlingsPeriode: Periode,
        ): Formuegrunnlag = tryCreate(id, opprettet, periode, epsFormue, søkersFormue, begrunnelse, bosituasjon, behandlingsPeriode).getOrHandle { throw IllegalArgumentException("Kunne ikke lage Formuegrunnlag") }

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
            if (epsFormue != null) {
                if (!(bosituasjon.harEktefelle())) {
                    return KunneIkkeLageFormueGrunnlag.MåHaEpsHvisManHarSattEpsFormue.left()
                }
                if (!(bosituasjon.periode.inneholder(periode))) {
                    return KunneIkkeLageFormueGrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode.left()
                }
            }

            if (!(behandlingsPeriode.inneholder(periode))) {
                return KunneIkkeLageFormueGrunnlag.FormuePeriodeErUtenforBehandlingsperioden.left()
            }
            return Formuegrunnlag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                epsFormue = epsFormue,
                søkersFormue = søkersFormue,
                begrunnelse = begrunnelse,
            ).right()
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
