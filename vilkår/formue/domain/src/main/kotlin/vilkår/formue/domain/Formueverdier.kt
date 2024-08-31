package vilkår.formue.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder

data class Formueverdier private constructor(
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
        ): Formueverdier = tryCreate(
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
        ): Either<KunneIkkeLageFormueVerdier, Formueverdier> {
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

            return Formueverdier(
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

        fun empty() = create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 0,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        )

        fun List<Formuegrunnlag>.minsteAntallSammenhengendePerioder(): SlåttSammenIkkeOverlappendePerioder {
            return map { it.periode }.minsteAntallSammenhengendePerioder()
        }
    }
}
