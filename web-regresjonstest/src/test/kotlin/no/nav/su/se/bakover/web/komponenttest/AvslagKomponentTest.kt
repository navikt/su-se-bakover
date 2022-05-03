package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettAvslåttSøknadsbehandling
import org.junit.jupiter.api.Test
import java.util.UUID

class AvslagKomponentTest {
    @Test
    fun `teste avslag`() {
        withKomptestApplication(
            clock = fixedClock,
        ) { appComponents ->
            val fnr = Fnr.generer()

            val sakId = opprettAvslåttSøknadsbehandling(
                fnr = fnr.toString(),
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)
                UUID.fromString(sakId)
            }
            val sak = appComponents.databaseRepos.sak.hentSak(sakId = sakId)!!
            sak.søknadsbehandlinger.first() shouldBe beOfType<Søknadsbehandling.Iverksatt.Avslag.UtenBeregning>()
            sak.vedtakListe.first() shouldBe beOfType<Avslagsvedtak.AvslagVilkår>()
            (sak.vedtakListe.first() as Avslagsvedtak).avslagsgrunner shouldBe listOf(
                Avslagsgrunn.UFØRHET,
                Avslagsgrunn.FLYKTNING,
            )
        }
    }
}
