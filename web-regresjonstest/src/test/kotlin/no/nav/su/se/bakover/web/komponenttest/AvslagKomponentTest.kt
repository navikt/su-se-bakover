package no.nav.su.se.bakover.web.komponenttest

import dokument.domain.Dokument
import dokument.domain.brev.HentDokumenterForIdType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagVilkår
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeType
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
                client = this.client,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)
                UUID.fromString(sakId)
            }
            val sak = appComponents.databaseRepos.sak.hentSak(sakId = sakId)!!

            sak.vedtakListe.first().shouldBeType<VedtakAvslagVilkår>().let { avslagsvedtak ->
                avslagsvedtak.behandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>()
                avslagsvedtak.avslagsgrunner shouldBe listOf(
                    Avslagsgrunn.UFØRHET,
                    Avslagsgrunn.FLYKTNING,
                )
                appComponents.services.brev.hentDokumenterFor(HentDokumenterForIdType.HentDokumenterForVedtak(avslagsvedtak.id)).let {
                    it shouldHaveSize 1
                    it.first().shouldBeType<Dokument.MedMetadata.Vedtak>().let { dokument ->
                        dokument.generertDokument shouldNotBe null
                        dokument.generertDokumentJson shouldNotBe null
                        dokument.tittel shouldBe "Avslag supplerende stønad"
                        dokument.metadata.vedtakId shouldBe avslagsvedtak.id

                        appComponents.databaseRepos.dokumentRepo.hentDokumenterForJournalføring()
                            .let { skalJournalføres ->
                                skalJournalføres shouldHaveSize 1
                                skalJournalføres.first().dokument.shouldBeEqualToIgnoringFields(
                                    dokument,
                                    Dokument::generertDokument,
                                )
                            }
                    }
                }
            }
        }
    }
}
