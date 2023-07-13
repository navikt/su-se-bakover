package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

data class Uføregrad private constructor(
    val value: Int,
) {
    companion object {
        fun tryParse(uføregrad: Int): Either<UføregradMåVæreMellomEnOgHundre, Uføregrad> {
            if (uføregrad < 1 || uføregrad > 100) {
                return UføregradMåVæreMellomEnOgHundre.left()
            }
            return Uføregrad(uføregrad).right()
        }

        fun parse(uføregrad: Int): Uføregrad {
            return tryParse(uføregrad).getOrElse {
                throw IllegalArgumentException(it::class.simpleName)
            }
        }
    }

    data object UføregradMåVæreMellomEnOgHundre
}
