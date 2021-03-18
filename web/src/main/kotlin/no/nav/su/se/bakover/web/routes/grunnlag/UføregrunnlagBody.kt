package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson

internal data class UføregrunnlagBody(
    val periode: PeriodeJson,
    val uføregrad: Int,
    val forventetInntekt: Int,
) {

    fun toDomain(): Either<Resultat, Grunnlag.Uføregrunnlag> {
        val periode = periode.toPeriode().getOrHandle {
            return it.left()
        }
        val validUføregrad = Uføregrad.tryParse(uføregrad).getOrElse {
            return BadRequest.errorJson(
                message = "Uføregrad må være mellom en og hundre",
                code = "uføregrad_må_være_mellom_en_og_hundre",
            ).left()
        }
        return Grunnlag.Uføregrunnlag(
            periode = periode,
            uføregrad = validUføregrad,
            forventetInntekt = forventetInntekt
        ).right()
    }
}

internal fun List<UføregrunnlagBody>.toDomain(): Either<Resultat, List<Grunnlag.Uføregrunnlag>> {
    return this.map {
        it.toDomain()
    }.traverse(Either.applicative(), ::identity).fix().map {
        it.fix()
    }
}
