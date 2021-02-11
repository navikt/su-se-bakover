package no.nav.su.se.bakover.domain.visitor

interface Visitor

interface Visitable<in T : Visitor> {
    fun accept(visitor: T)
}
