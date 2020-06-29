package no.nav.su.se.bakover.domain

abstract class PersistentDomainObject<T : PersistenceObserver>(
    protected val id: Long
) {
    protected lateinit var persistenceObserver: T
    fun addObserver(observer: T) {
        if (this::persistenceObserver.isInitialized) throw PersistenceObserverException()
        this.persistenceObserver = observer
    }
}

class PersistenceObserverException(
    message: String = "There should only be one instance of type ${PersistenceObserver::class} assigned to an object!"
) : RuntimeException(message)

interface PersistenceObserver
interface VoidObserver : PersistenceObserver
