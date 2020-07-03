package no.nav.su.se.bakover.common

import arrow.core.Either
import arrow.core.orNull

fun <L, R> Either<L, R>.rightValue() = orNull()!!

fun <L, R> Either<L, R>.leftValue() = swap().orNull()!!

class ArrowExtensions
