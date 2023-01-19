package no.nav.su.se.bakover.domain.visitor

interface Visitor

// TODO jah: Slett denne klassen og andre visitors og flytt logikken nærmere der den bør bo.
interface Visitable<in T : Visitor> {
    fun accept(visitor: T)
}
