package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import org.junit.jupiter.api.Test
import java.util.UUID

internal class NySøknadKomponentTest {
    @Test
    fun `innsending av ny førstegangssøknad søknad oppretter sak, journalfører søknad og oppretter oppgave`() {
        withKomptestApplication(
            clock = fixedClock,
        ) { appComponents ->
            val (sakId, søknadId) = nyDigitalSøknad(client = this.client).let {
                UUID.fromString(NySøknadJson.Response.hentSakId(it)) to UUID.fromString(NySøknadJson.Response.hentSøknadId(it))
            }
            val sak = appComponents.services.sak.hentSak(sakId).getOrFail()
            appComponents.services.søknad.hentSøknad(søknadId).getOrFail().also { søknad ->
                søknad.shouldBeType<Søknad.Journalført.MedOppgave.IkkeLukket>().let {
                    it.fnr shouldBe sak.fnr
                    it.type shouldBe Sakstype.UFØRE
                    it.innsendtAv shouldBe NavIdentBruker.Veileder("Z990Lokal")
                    it.oppgaveId shouldBe OppgaveId("stubbedOppgaveId")
                    it.journalpostId shouldBe JournalpostId("1")
                }
            }
        }
    }
}
