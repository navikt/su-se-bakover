package no.nav.su.se.bakover.common

sealed class Either<out L, out R> {
    data class Left<E>(val left: E) : Either<E, Nothing>()
    data class Right<V>(val right: V) : Either<Nothing, V>()

    inline fun <A> fold(
        left: (L) -> A,
        right: (R) -> A
    ): A = when (this) {
        is Left -> left(this.left)
        is Right -> right(this.right)
    }
}
