package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class PersistentDomainObjectTest {
    @Test
    fun `throw exception if multiple persistence observers assigned`() {
        val sakId = UUID.randomUUID()
        val sak = Sak(id = sakId, fnr = Fnr("12345678910"), oppdrag = Oppdrag(sakId = sakId))
        assertDoesNotThrow { sak.addObserver(someObserver) }
        assertThrows<PersistenceObserverException> { sak.addObserver(someObserver) }
    }

    @Test
    fun `throw exception if unassigned persistence observer is invoked`() {
        val sakId = UUID.randomUUID()
        val sak = Sak(id = sakId, fnr = Fnr("12345678910"), oppdrag = Oppdrag(sakId = sakId))
        assertThrows<UninitializedPropertyAccessException> { sak.nySøknad(SøknadInnholdTestdataBuilder.build()) }
    }

    private val someObserver = object : SakPersistenceObserver {
        private val testSøknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())
        override fun nySøknad(sakId: UUID, søknad: Søknad): Søknad = søknad
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) =
            Behandling(søknad = testSøknad, sakId = sakId)

        override fun nyStoppbehandling(nyBehandling: Stoppbehandling.Simulert) = nyBehandling
        override fun hentPågåendeStoppbehandling(sakId: UUID) = null
    }
}
