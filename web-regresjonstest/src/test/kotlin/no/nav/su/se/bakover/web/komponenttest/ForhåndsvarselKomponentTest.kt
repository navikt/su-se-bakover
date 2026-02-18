package no.nav.su.se.bakover.web.komponenttest

import arrow.core.getOrElse
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentFormaal
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.mottaker.DistribueringsadresseRequest
import no.nav.su.se.bakover.domain.mottaker.LagreMottaker
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
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
import org.junit.jupiter.api.fail
import tilgangstyring.application.TilgangstyringService
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

    @Test
    fun `oppretter kopi av forhandsvarsel for ekstra mottaker`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = simulertRevurdering(this.client, appComponents)
            val ekstraMottakerFnr = Fnr.generer()

            appComponents.services.mottakerService.lagreMottaker(
                mottaker = LagreMottaker(
                    navn = "Ekstra mottaker",
                    foedselsnummer = ekstraMottakerFnr.toString(),
                    adresse = DistribueringsadresseRequest(
                        adresselinje1 = "Ekstragate 1",
                        adresselinje2 = null,
                        adresselinje3 = null,
                        postnummer = "0001",
                        poststed = "Oslo",
                    ),
                    referanseId = revurderingId,
                    referanseType = ReferanseTypeMottaker.REVURDERING.name,
                    brevtype = DokumentFormaal.FORHANDSVARSEL.name,
                ),
                sakId = UUID.fromString(sakid),
            ).getOrElse {
                fail("Kunne ikke lagre ekstra mottaker for forhåndsvarsel")
            }

            sendForhåndsvarsel(
                sakId = sakid,
                behandlingId = revurderingId,
                client = this.client,
            )

            val forhåndsvarsler = appComponents.services.brev.hentDokumenterFor(
                HentDokumenterForIdType.HentDokumenterForSak(UUID.fromString(sakid)),
            ).filterIsInstance<Dokument.MedMetadata.Informasjon.Viktig>().filter {
                it.metadata.revurderingId == UUID.fromString(revurderingId)
            }

            forhåndsvarsler shouldHaveSize 2
            forhåndsvarsler.count { it.erKopi } shouldBe 1

            val kopi = forhåndsvarsler.first { it.erKopi }
            kopi.ekstraMottaker shouldBe ekstraMottakerFnr.toString()
            kopi.navnEkstraMottaker shouldBe "Ekstra mottaker"
            kopi.distribusjonstype shouldBe Distribusjonstype.VIKTIG
        }
    }

    @Test
    fun `forhandsvarsel med ekstra mottaker journalfores og distribueres for original og kopi`() {
        withKomptestApplication(
            clock = TikkendeKlokke(1.oktober(2021).fixedClock()),
        ) { appComponents ->
            val (sakid, revurderingId) = simulertRevurdering(this.client, appComponents)
            val ekstraMottakerFnr = Fnr.generer()

            appComponents.services.mottakerService.lagreMottaker(
                mottaker = LagreMottaker(
                    navn = "Ekstra mottaker",
                    foedselsnummer = ekstraMottakerFnr.toString(),
                    adresse = DistribueringsadresseRequest(
                        adresselinje1 = "Ekstragate 1",
                        adresselinje2 = null,
                        adresselinje3 = null,
                        postnummer = "0001",
                        poststed = "Oslo",
                    ),
                    referanseId = revurderingId,
                    referanseType = ReferanseTypeMottaker.REVURDERING.name,
                    brevtype = DokumentFormaal.FORHANDSVARSEL.name,
                ),
                sakId = UUID.fromString(sakid),
            ).getOrElse {
                fail("Kunne ikke lagre ekstra mottaker for forhåndsvarsel")
            }

            sendForhåndsvarsel(
                sakId = sakid,
                behandlingId = revurderingId,
                client = this.client,
            )

            val journalførDokumentService = JournalførDokumentService(
                journalførBrevClient = appComponents.clients.journalførClients.brev,
                dokumentRepo = appComponents.databaseRepos.dokumentRepo,
                sakService = appComponents.services.sak,
            )
            val distribuerDokumentService = DistribuerDokumentService(
                dokDistFordeling = appComponents.clients.dokDistFordeling,
                dokumentRepo = appComponents.databaseRepos.dokumentRepo,
                dokumentHendelseRepo = appComponents.databaseRepos.dokumentHendelseRepo,
                distribuerDokumentHendelserKonsument = appComponents.dokumentHendelseKomponenter.services.distribuerDokumentHendelserKonsument,
                tilgangstyringService = TilgangstyringService(appComponents.services.person),
                clock = appComponents.clock,
            )

            journalførDokumentService.journalfør()
            distribuerDokumentService.distribuer()

            val forhåndsvarsler = appComponents.services.brev.hentDokumenterFor(
                HentDokumenterForIdType.HentDokumenterForSak(UUID.fromString(sakid)),
            ).filterIsInstance<Dokument.MedMetadata.Informasjon.Viktig>().filter {
                it.metadata.revurderingId == UUID.fromString(revurderingId)
            }

            forhåndsvarsler.shouldHaveSize(2)
            forhåndsvarsler.count { it.erKopi } shouldBe 1
            forhåndsvarsler.forEach {
                it.erJournalført() shouldBe true
                it.erBrevBestilt() shouldBe true
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
