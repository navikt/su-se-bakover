package no.nav.su.se.bakover

sealed class Either<out E, out V> {
    data class Error<E>(val error: E): Either<E, Nothing>()
    data class Value<V>(val value: V): Either<Nothing, V>()

    inline fun <A> fold(
        onError: (E) -> A,
        onValue: (V) -> A
    ): A = when(this) {
        is Error -> onError(this.error)
        is Value -> onValue(this.value)
    }
}