package vilkår.uføre.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

/**
 * I praksis vil ikke denne kunne være under 40%, men vi har ikke hatt behovet for å legge inn denne begrensningen.
 *
 * https://lovdata.no/lov/2005-04-29-21/§2 del 1, setning 2. .. fyller vilkåra i folketrygdlova §§ 12-4 til 12-7.
 * https://lovdata.no/lov/1997-02-28-19/§12-7 referer til uføregrad.
 */
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
