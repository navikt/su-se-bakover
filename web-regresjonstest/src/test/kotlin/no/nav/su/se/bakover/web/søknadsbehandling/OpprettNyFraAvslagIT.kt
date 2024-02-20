package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.SakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.lukkSøknad
import no.nav.su.se.bakover.web.vedtak.VedtakJson
import no.nav.su.se.bakover.web.vedtak.opprettNySøknadsbehandlingFraVedtak
import org.junit.jupiter.api.Test

class OpprettNyFraAvslagIT {

    // tester i tillegg at man ikke får opprettet ny behandling fra avslaget dersom søknaden senere blir lukket
    @Test
    fun `kan opprette ny søknadsbehandling fra et avslagsvedtak`() {
        withTestApplicationAndEmbeddedDb(clock = tikkendeFixedClock()) { appComponents ->
            val (sakId, søknadId) = opprettAvslåttSøknadsbehandling(client = this.client).let {
                Pair(BehandlingJson.hentSakId(it), BehandlingJson.hentSøknadId(it))
            }
            val sak = hentSak(sakId, client = this.client)
            // avslagsvedtaket fra opprettAvslåttSøknadsbehandling
            val vedtakId = SakJson.hentFørsteVedtak(sak).let {
                VedtakJson.hentVedtakId(it)
            }

            appComponents.opprettNySøknadsbehandlingFraVedtak(sakId, vedtakId, this.client, søknadId)

            appComponents.lukkSøknad(søknadId, this.client)

            appComponents.opprettNySøknadsbehandlingFraVedtak(
                sakId,
                vedtakId,
                this.client,
                søknadId,
                verifiserRespons = false,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
            )
        }
    }
}
