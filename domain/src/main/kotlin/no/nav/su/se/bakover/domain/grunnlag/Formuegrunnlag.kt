package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
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
            ).getOrElse { throw IllegalArgumentException(it.toString()) }

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

            fun List<Formuegrunnlag>.minsteAntallSammenhengendePerioder(): List<Periode> {
                return map { it.periode }.minsteAntallSammenhengendePerioder()
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
            // Denne tar ikke høyde for søknadsbehandling da denne ikke nødvendigvis er fullstendig
            bosituasjon: List<Grunnlag.Bosituasjon.Fullstendig>,
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

            konsistenssjekk(
                /*
                 * Mismatch å sjekke 1 fradragsgrunnlag mot mange bosituasjoner, men gir mening innenfor samme periode.
                 */
                bosituasjon = bosituasjon.lagTidslinje(periode),
                formuegrunnlag = listOf(formuegrunnlag),
            ).getOrElse { return it.left() }

            return formuegrunnlag.right()
        }

        @JvmName("tryCreatePotensieltUfullstendigBosituasjon")
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            epsFormue: Verdier?,
            søkersFormue: Verdier,
            // Tillater ufullstending for å kunne bruke med søknadsbehandling
            bosituasjon: List<Grunnlag.Bosituasjon>,
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

            konsistenssjekk(
                bosituasjon = bosituasjon,
                formuegrunnlag = listOf(formuegrunnlag),
            ).getOrElse {
                when (it) {
                    is KunneIkkeLageFormueGrunnlag.Konsistenssjekk -> {
                        // TODO("flere_satser fritar denne fra å ha fullstendig bosituasjon - det er midlertidig gyldig på søknadsbehandling)
                        when (it.feil) {
                            is Konsistensproblem.BosituasjonOgFormue.UgyldigBosituasjon -> {
                                it.feil.feil.singleOrNull { it is Konsistensproblem.Bosituasjon.Ufullstendig }?.let {
                                    Unit.right()
                                } ?: it.left()
                            }

                            else -> it.left()
                        }
                    }

                    else -> it.left()
                }
            }

            return formuegrunnlag.right()
        }

        private fun konsistenssjekk(
            bosituasjon: List<Grunnlag.Bosituasjon>,
            formuegrunnlag: List<Formuegrunnlag>,
        ): Either<KunneIkkeLageFormueGrunnlag, Unit> {
            return SjekkOmGrunnlagErKonsistent.BosituasjonOgFormue(
                bosituasjon = bosituasjon,
                formue = formuegrunnlag,
            ).resultat.mapLeft { problem ->
                problem.first().let { KunneIkkeLageFormueGrunnlag.Konsistenssjekk(it) }
            }
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
    object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeLageFormueGrunnlag
    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLageFormueGrunnlag
}

sealed interface KunneIkkeLageFormueVerdier {
    object DepositumErStørreEnnInnskudd : KunneIkkeLageFormueVerdier
    object VerdierKanIkkeVæreNegativ : KunneIkkeLageFormueVerdier
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
