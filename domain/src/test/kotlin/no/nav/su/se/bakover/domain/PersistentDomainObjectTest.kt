package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class PersistentDomainObjectTest {
    @Test
    fun `throw exception if multiple persistence observers assigned`() {
        val sakId = UUID.randomUUID()
        val sak = Sak(
            id = sakId,
            fnr = Fnr("12345678910"),
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId
            )
        )
        assertDoesNotThrow { sak.addObserver(someObserver) }
        assertThrows<PersistenceObserverException> { sak.addObserver(someObserver) }
    }

    @Test
    fun `throw exception if unassigned persistence observer is invoked`() {
        val sakId = UUID.randomUUID()
        val sak = Sak(
            id = sakId,
            fnr = Fnr("12345678910"),
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId
            ),
            søknader = listOf(
                Søknad(
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build()
                )
            ).toMutableList()
        )
        assertThrows<UninitializedPropertyAccessException> { sak.opprettSøknadsbehandling(sak.søknader().first().id) }
    }

    private val someObserver = object : SakPersistenceObserver {
        private val testSøknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build())
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) =
            Behandling(søknad = testSøknad, sakId = sakId)
    }
}
