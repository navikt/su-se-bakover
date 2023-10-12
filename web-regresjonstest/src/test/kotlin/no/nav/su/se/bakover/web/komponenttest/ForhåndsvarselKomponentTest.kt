package no.nav.su.se.bakover.web.komponenttest

import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.revurdering.beregnOgSimuler
import no.nav.su.se.bakover.web.revurdering.forhåndsvarsel.sendForhåndsvarsel
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import java.util.UUID

class ForhåndsvarselKomponentTest {

    @Test
    fun `oppretter og bestiller brev for forhåndsvarslinger`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = simulertRevurdering(this.client, appComponents)

            sendForhåndsvarsel(
                sakId = sakid,
                behandlingId = revurderingId,
                client = this.client,
            )

            sendForhåndsvarsel(
                sakId = sakid,
                behandlingId = revurderingId,
                client = this.client,
            )

            appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForRevurdering(UUID.fromString(revurderingId))).let {
                it shouldHaveSize 2
                it.forEach { dokument ->
                    dokument.shouldBeType<Dokument.MedMetadata.Informasjon.Viktig>().let { forhåndsvarsel ->
                        forhåndsvarsel.tittel shouldBe "Varsel om at vi vil ta opp stønaden din til ny vurdering"
                        forhåndsvarsel.distribusjonstype shouldBe Distribusjonstype.VIKTIG
                        forhåndsvarsel.metadata.revurderingId shouldBe UUID.fromString(revurderingId)
                        forhåndsvarsel.metadata.sakId shouldBe UUID.fromString(sakid)
                    }
                }
            }
        }
    }

    private fun simulertRevurdering(
        client: HttpClient,
        appComponents: AppComponents,
    ): Pair<String, String> {
        return opprettInnvilgetSøknadsbehandling(
            fnr = Fnr.generer().toString(),
            fraOgMed = 1.januar(2021).toString(),
            tilOgMed = 31.desember(2021).toString(),
            client = client,
            appComponents = appComponents,
        ).let { søknadsbehandlingJson ->
            val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

            val revurderingId = opprettRevurdering(
                sakId = sakId,
                fraOgMed = 1.mai(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
                client = client,
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            beregnOgSimuler(
                sakId = sakId,
                behandlingId = revurderingId,
                client = client,
            )
            sakId to revurderingId
        }
    }
}
