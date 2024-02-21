package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.beregning.beregn
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.fastopphold.leggTilFastOppholdINorge
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.avslåttFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.formue.leggTilFormue
import no.nav.su.se.bakover.web.søknadsbehandling.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.søknadsbehandling.iverksett.iverksett
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilInstitusjonsopphold
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilLovligOppholdINorge
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilUtenlandsopphold
import no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte.leggTilPersonligOppmøte
import no.nav.su.se.bakover.web.søknadsbehandling.sendTilAttestering.sendTilAttestering
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilStønadsperiode

/**
 * Oppretter en ny søknad med søknadbehandling.
 * @param fnr Dersom det finnes en sak for dette fødselsnumret fra før, vil det knyttes til den eksisterende saken.
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun opprettAvslåttSøknadsbehandlingPgaVilkår(
    fnr: String = Fnr.generer().toString(),
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
    client: HttpClient,
): String {
    val søknadResponseJson = nyDigitalSøknad(
        fnr = fnr,
        client = client,
    )
    return opprettAvslåttSøknadsbehandlingPgaVilkår(
        sakId = NySøknadJson.Response.hentSakId(søknadResponseJson),
        søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson),
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
}

/**
 * Oppretter en avslått søknadbehandling på en eksisterende sak og søknad
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun opprettAvslåttSøknadsbehandlingPgaVilkår(
    sakId: String,
    søknadId: String,
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
    client: HttpClient,
): String {
    val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
        sakId = sakId,
        søknadId = søknadId,
        client = client,
    )
    val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)

    leggTilStønadsperiode(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
    leggTilUføregrunnlag(
        sakId = sakId,
        behandlingId = behandlingId,
        resultat = "VilkårIkkeOppfylt",
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
    leggTilFlyktningVilkår(
        sakId = sakId,
        body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
        behandlingId = behandlingId,
        client = client,
    )
    sendTilAttestering(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    return iverksett(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
        appComponents = null,
    )
}

/**
 * Oppretter en ny søknad med søknadbehandling.
 * @param fnr Dersom det finnes en sak for dette fødselsnumret fra før, vil det knyttes til den eksisterende saken.
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun opprettAvslåttSøknadsbehandlingPgaBeregning(
    fnr: String = Fnr.generer().toString(),
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
    client: HttpClient,
): String {
    val søknadResponseJson = nyDigitalSøknad(
        fnr = fnr,
        client = client,
    )
    return opprettAvslåttSøknadsbehandlingPgaBeregning(
        sakId = NySøknadJson.Response.hentSakId(søknadResponseJson),
        søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson),
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
}

/**
 * Oppretter en avslått søknadbehandling på en eksisterende sak og søknad
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun opprettAvslåttSøknadsbehandlingPgaBeregning(
    sakId: String,
    søknadId: String,
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
    client: HttpClient,
): String {
    val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
        sakId = sakId,
        søknadId = søknadId,
        client = client,
    )
    val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)

    leggTilStønadsperiode(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
    leggTilUføregrunnlag(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        client = client,
    )
    leggTilFlyktningVilkår(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilLovligOppholdINorge(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilFastOppholdINorge(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilInstitusjonsopphold(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilUtenlandsopphold(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilBosituasjon(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilFormue(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilPersonligOppmøte(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    leggTilFradrag(
        sakId = sakId,
        behandlingId = behandlingId,
        body = {
            """
                {
                  "fradrag": [
                    {
                      "periode": {"fraOgMed": "$fraOgMed", "tilOgMed": "$tilOgMed"},
                      "type": "PrivatPensjon",
                      "beløp": 35000.0,
                      "utenlandskInntekt": null,
                      "tilhører": "BRUKER"
                    }
                  ]
                }
                """
        },
        client = client,
    )
    beregn(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    sendTilAttestering(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
    )
    return iverksett(
        sakId = sakId,
        behandlingId = behandlingId,
        client = client,
        appComponents = null,
    )
}
