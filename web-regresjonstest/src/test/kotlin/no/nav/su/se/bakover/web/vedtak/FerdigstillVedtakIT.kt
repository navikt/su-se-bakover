package no.nav.su.se.bakover.web.vedtak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FerdigstillVedtakIT {

    @Test
    fun `ferdigstill vedtak er idempotent`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(fixedClock),
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.desember(2021)
            val fnr = Fnr.generer().toString()
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson).let { UUID.fromString(it) }
                val vedtakId = appComponents.databaseRepos.sak.hentSak(sakId)!!.vedtakListe.first().id
                // Vi forventer at opprettInnvilgetSøknadsbehandling allerede har emulert kvittering for utbetaling + ferdigstilt.
                appComponents.databaseRepos.dokumentRepo.hentForSak(sakId).size shouldBe 1
                appComponents.services.ferdigstillVedtak.ferdigstillVedtak(vedtakId)
                appComponents.databaseRepos.dokumentRepo.hentForSak(sakId).size shouldBe 1
            }
        }
    }
}
