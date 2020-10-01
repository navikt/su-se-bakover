package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldHaveSize
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SakTest {

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")

    private lateinit var eventObserver: EventObserver
    private lateinit var sak: Sak

    @Test
    fun `should handle nySøknad`() {
        sak.nySøknad(Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build()))
        eventObserver.events shouldHaveSize 1
        sak.behandlinger() shouldHaveSize 0
        sak.søknader() shouldHaveSize 1
    }

    class EventObserver : SakEventObserver {
        val events = mutableListOf<SakEventObserver.NySøknadEvent>()
        override fun nySøknadEvent(nySøknadEvent: SakEventObserver.NySøknadEvent) {
            events.add(nySøknadEvent)
        }
    }

    @BeforeEach
    fun beforeEach() {
        eventObserver = EventObserver()

        sak = nySak().also {
            it.addObserver(eventObserver)
        }
    }

    private fun nySak() = Sak(
        id = sakId,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId
        )
    )
}
