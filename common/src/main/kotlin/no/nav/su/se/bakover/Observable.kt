package no.nav.su.se.bakover

abstract class Observable<I> {
    protected val observers: MutableSet<I> = mutableSetOf()
    inline fun <reified T : Observable<I>> subscribe(vararg subscribers: I) = this.also { subscribers.forEach { observers.add(it) } } as T
    inline fun <reified T : Observable<I>> unsubscribe(vararg subscribers: I) = this.also { subscribers.forEach { observers.remove(it) } } as T
}
