package no.nav.su.se.bakover.web.søknadsbehandling

import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.leggTilOpplysningsplikt
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.beregning.beregn
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
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

internal val SKIP_STEP = "SKIP_STEP" // verdi som signaliserer at kallet skal hoppes over

/**
 * Oppretter en ny søknad med søknadbehandling.
 * @param fnr Dersom det finnes en sak for dette fødselsnumret fra før, vil det knyttes til den eksisterende saken.
 * @return Den nylig opprettede søknadsbehandlingen
 */
internal fun opprettInnvilgetSøknadsbehandling(
    fnr: String = Fnr.generer().toString(),
    fraOgMed: String = fixedLocalDate.startOfMonth().toString(),
    tilOgMed: String = fixedLocalDate.startOfMonth().plusMonths(11).endOfMonth().toString(),
    client: HttpClient,
    leggTilVirkningstidspunkt: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilVirkningstidspunkt(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilOpplysningsplikt: (behandlingId: String) -> String = { behandlingId ->
        leggTilOpplysningsplikt(
            behandlingId = behandlingId,
            type = "SØKNADSBEHANDLING",
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilUføregrunnlag: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilUføregrunnlag(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilFlyktningVilkår: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilFlyktningVilkår(
            sakId = sakId,
            behandlingId = behandlingId,
            body = { innvilgetFlyktningVilkårJson(fraOgMed, tilOgMed) },
            client = client,
        )
    },
    leggTilLovligOppholdINorge: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilLovligOppholdINorge(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilFastOppholdINorge: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilFastOppholdINorge(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilInstitusjonsopphold: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilInstitusjonsopphold(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilUtenlandsopphold: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilUtenlandsopphold(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilBosituasjon: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilBosituasjon(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilFormue: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilFormue(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilPersonligOppmøte: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilPersonligOppmøte(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    beregn: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        beregn(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
    simuler: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        simuler(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
    sendTilAttestering: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        sendTilAttestering(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
    iverksett: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        iverksett(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
): String {
    val søknadResponseJson = nyDigitalSøknad(
        fnr = fnr,
        client = client,
    )
    val sakId = NySøknadJson.Response.hentSakId(søknadResponseJson)
    val søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson)
    val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
        sakId = sakId,
        søknadId = søknadId,
        client = client,
    )
    val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)

    return listOf(
        leggTilVirkningstidspunkt(sakId, behandlingId),
        leggTilOpplysningsplikt(behandlingId),
        leggTilUføregrunnlag(sakId, behandlingId),
        leggTilFlyktningVilkår(sakId, behandlingId),
        leggTilLovligOppholdINorge(sakId, behandlingId),
        leggTilFastOppholdINorge(sakId, behandlingId),
        leggTilInstitusjonsopphold(sakId, behandlingId),
        leggTilUtenlandsopphold(sakId, behandlingId),
        leggTilBosituasjon(sakId, behandlingId),
        leggTilFormue(sakId, behandlingId),
        leggTilPersonligOppmøte(sakId, behandlingId),
        beregn(sakId, behandlingId),
        simuler(sakId, behandlingId),
        sendTilAttestering(sakId, behandlingId),
        iverksett(sakId, behandlingId),
    ).map { it }.last { it != SKIP_STEP } // returner siste verdi hvis steg ikke er hoppet over
}
