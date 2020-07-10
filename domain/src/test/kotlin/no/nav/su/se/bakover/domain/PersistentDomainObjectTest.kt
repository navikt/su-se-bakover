package no.nav.su.se.bakover.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class PersistentDomainObjectTest {
    @Test
    fun `throw exception if multiple persistence observers assigned`() {
        val sak = Sak(fnr = Fnr("12345678910"))
        assertDoesNotThrow { sak.addObserver(someObserver) }
        assertThrows<PersistenceObserverException> { sak.addObserver(someObserver) }
    }

    @Test
    fun `throw exception if unassigned persistence observer is invoked`() {
        val sak = Sak(fnr = Fnr("12345678910"))
        assertThrows<UninitializedPropertyAccessException> { sak.nySøknad(SøknadInnholdTestdataBuilder.build()) }
    }

    private val someObserver = object : SakPersistenceObserver {
        private val testSøknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())
        override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad = søknad
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) = Behandling(søknad = testSøknad)
    }
}
