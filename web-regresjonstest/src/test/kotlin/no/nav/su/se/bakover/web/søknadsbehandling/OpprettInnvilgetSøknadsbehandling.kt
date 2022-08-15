package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.leggTilOpplysningsplikt
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.beregning.beregn
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.fullførBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.taStillingTilEps
import no.nav.su.se.bakover.web.søknadsbehandling.fastopphold.leggTilFastOppholdINorge
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.innvilgetFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.formue.leggTilFormue
import no.nav.su.se.bakover.web.søknadsbehandling.iverksett.iverksett
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilInstitusjonsopphold
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilLovligOppholdINorge
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilUtenlandsopphold
import no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte.leggTilPersonligOppmøte
import no.nav.su.se.bakover.web.søknadsbehandling.sendTilAttestering.sendTilAttestering
import no.nav.su.se.bakover.web.søknadsbehandling.simulering.simuler
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import java.time.LocalDate

/**
 * Oppretter en ny søknad med søknadbehandling.
 * @param fnr Dersom det finnes en sak for dette fødselsnumret fra før, vil det knyttes til den eksisterende saken.
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun ApplicationTestBuilder.opprettInnvilgetSøknadsbehandling(
    fnr: String = Fnr.generer().toString(),
    fraOgMed: String = LocalDate.now().startOfMonth().toString(),
    tilOgMed: String = LocalDate.now().startOfMonth().plusMonths(11).endOfMonth().toString(),
): String {
    val søknadResponseJson = nyDigitalSøknad(
        fnr = fnr,
    )
    return opprettInnvilgetSøknadsbehandling(
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
internal fun ApplicationTestBuilder.opprettInnvilgetSøknadsbehandling(
    sakId: String,
    søknadId: String,
    fraOgMed: String = LocalDate.now().startOfMonth().toString(),
    tilOgMed: String = LocalDate.now().startOfMonth().plusMonths(11).endOfMonth().toString(),
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
    leggTilOpplysningsplikt(
        behandlingId = behandlingId,
        type = "SØKNADSBEHANDLING",
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilUføregrunnlag(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilFlyktningVilkår(
        sakId = sakId,
        behandlingId = behandlingId,
        body = { innvilgetFlyktningVilkårJson(fraOgMed, tilOgMed) },
    )
    leggTilLovligOppholdINorge(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilFastOppholdINorge(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed
    )
    leggTilInstitusjonsopphold(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilUtenlandsopphold(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    taStillingTilEps(
        sakId = sakId,
        behandlingId = behandlingId,
    )
    leggTilFormue(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
    )
    leggTilPersonligOppmøte(
        sakId = sakId,
        behandlingId = behandlingId,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed
    )
    fullførBosituasjon(
        sakId = sakId,
        behandlingId = behandlingId,
    )
    // Hopper over fradrag for å gjøre det enklere
    beregn(
        sakId = sakId,
        behandlingId = behandlingId,
    )
    simuler(
        sakId = sakId,
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
