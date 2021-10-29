package no.nav.su.se.bakover.common

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlin.math.abs

/**
 * Regner ut økningen fra a til b.
 * */
fun procentuellDifferens(a: Int, b: Int): Either<KanIkkeDeleMed0, Double> =
    when (a) {
        0 -> Left(KanIkkeDeleMed0)
        else -> Right(
            (b.toDouble() - a.toDouble()) / abs(a.toDouble()),
        )
    }

object KanIkkeDeleMed0
