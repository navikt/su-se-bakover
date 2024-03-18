package tilbakekreving.infrastructure.client.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right

enum class Alvorlighetsgrad(val value: String) {
    OK("00"),

    /** En varselmelding følger med */
    OK_MED_VARSEL("04"),

    /** Alvorlig feil som logges og stopper behandling av aktuelt tilfelle*/
    ALVORLIG_FEIL("08"),
    SQL_FEIL("12"),
    ;

    override fun toString() = value

    companion object {
        fun fromString(alvorlighetsgrad: String): Either<UkjentAlvorlighetsgrad, Alvorlighetsgrad> {
            return entries.firstOrNull { it.value == alvorlighetsgrad }?.right() ?: UkjentAlvorlighetsgrad(alvorlighetsgrad).left()
        }
    }
    data class UkjentAlvorlighetsgrad(val value: String)
}
