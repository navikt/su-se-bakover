package no.nav.su.se.bakover.web.søknadsbehandling.formue

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.hent.hentFormueVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.hent.hentSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

internal class MåInnhenteMerInformasjonOmFormueIT {
    @Test
    fun `Må innhente mer informasjon om formue hentes`() {
        // Minste antall steg for å velge `måInnhenteMerInformasjon`.
        // Dersom dette endrer seg, må testen tilpasses.
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val fraOgMed = "2021-01-01"
            val tilOgMed = "2021-12-31"
            val fnr = Fnr.generer().toString()
            val søknadResponseJson = nyDigitalSøknad(fnr = fnr, client = this.client)
            val sakId = NySøknadJson.Response.hentSakId(søknadResponseJson)
            val søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson)
            val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
                client = this.client,
            )
            val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)

            leggTilVirkningstidspunkt(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                client = this.client,
            )
            leggTilBosituasjon(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                client = this.client,
            )
            leggTilFormue(
                sakId = sakId,
                behandlingId = behandlingId,
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
                måInnhenteMerInformasjon = true,
                client = this.client,
            )

            //language=JSON
            val exptected = """
            {
              "vurderinger":[
                {
                  "grunnlag":{
                    "søkersFormue":{
                      "innskudd":0,
                      "verdipapir":0,
                      "pengerSkyldt":0,
                      "verdiKjøretøy":0,
                      "verdiIkkePrimærbolig":0,
                      "depositumskonto":0,
                      "kontanter":0,
                      "verdiEiendommer":0
                    },
                    "epsFormue":null
                  },
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "id":"1ff701bf-1c5a-46cb-a5ba-8b258ed4e9c3",
                  "resultat":"MåInnhenteMerInformasjon",
                  "periode":{
                    "fraOgMed":"2021-01-01",
                    "tilOgMed":"2021-12-31"
                  }
                }
              ],
              "formuegrenser":[
                {
                  "beløp":50676,
                  "gyldigFra":"2020-05-01"
                }
              ],
              "resultat":"MåInnhenteMerInformasjon"
            }
            """.trimIndent()
            val actual = hentFormueVilkår(hentSøknadsbehandling(sakId, behandlingId, client = this.client))
            JSONAssert.assertEquals(
                exptected,
                actual,
                CustomComparator(
                    JSONCompareMode.STRICT,
                    Customization(
                        "vurderinger[*].id",
                    ) { _, _ -> true },
                ),
            )
        }
    }
}
