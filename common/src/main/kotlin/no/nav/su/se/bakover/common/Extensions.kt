package no.nav.su.se.bakover.common

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import java.lang.Double.max
import java.lang.Double.min
import java.math.BigDecimal
import java.math.RoundingMode

fun <A> Either.Companion.unsafeCatch(f: () -> A) =
    runBlocking {
        catch { f() }
    }

// Lager vår egen her fordi Arrow sin filterMap bruker Option, som Arrow selv sier er deprecated (noe som feiler bygget vårt)
fun <A, B> List<A>.filterMap(predicate: Function1<A, B?>): List<B> =
    fold(emptyList()) { acc, a ->
        predicate(a).let {
            when (it) {
                null -> acc
                else -> acc.plus(it)
            }
        }
    }

fun Double.positiveOrZero() = max(0.0, this)
fun Double.limitedUpwardsTo(limit: Double) = min(limit, this)
fun Double.roundToDecimals(decimals: Int) = BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()

fun <A, B> Pair<A, A>.mapBoth(f: (A) -> B) =
    Pair(f(first), f(second))
