package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.avslåttFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.iverksett.iverksett
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.sendTilAttestering.sendTilAttestering
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt

/**
 * Oppretter en ny søknad med søknadbehandling.
 * @param fnr Dersom det finnes en sak for dette fødselsnumret fra før, vil det knyttes til den eksisterende saken.
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun ApplicationTestBuilder.opprettAvslåttSøknadsbehandling(
    fnr: String = Fnr.generer().toString(),
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
): String {
    val søknadResponseJson = nyDigitalSøknad(
        fnr = fnr,
    )
    return opprettAvslåttSøknadsbehandling(
        sakId = NySøknadJson.Response.hentSakId(søknadResponseJson),
        søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson),
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
}

/**
 * Oppretter en innvilget søknadbehandling på en eksisterende sak og søknad
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun ApplicationTestBuilder.opprettAvslåttSøknadsbehandling(
    sakId: String,
    søknadId: String,
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
): String {
    val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
        sakId = sakId,
        søknadId = søknadId,
    )
    val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)

    leggTilVirkningstidspunkt(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilUføregrunnlag(
        sakId = sakId,
        behandlingId = behandlingId,
        resultat = "VilkårIkkeOppfylt",
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilFlyktningVilkår(
        sakId = sakId,
        body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
        behandlingId = behandlingId,
    )
    sendTilAttestering(
        sakId = sakId,
        behandlingId = behandlingId,
    )
    return iverksett(
        sakId = sakId,
        behandlingId = behandlingId,
    )
}
