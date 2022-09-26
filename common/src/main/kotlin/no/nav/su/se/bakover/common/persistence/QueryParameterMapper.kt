package no.nav.su.se.bakover.common.persistence

import arrow.core.Either
import java.sql.PreparedStatement

/**
 * Maps custom types in a prepared statement's query parameters.
 */
interface QueryParameterMapper {
    fun tryMap(idx: Int, v: Any?): Either<TypeStøttesIkke, (preparedStatement: PreparedStatement) -> Unit>

    object TypeStøttesIkke
}
