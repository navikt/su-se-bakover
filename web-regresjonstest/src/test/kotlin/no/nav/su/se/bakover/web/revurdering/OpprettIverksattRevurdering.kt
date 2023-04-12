package no.nav.su.se.bakover.web.revurdering

import io.ktor.client.HttpClient
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.revurdering.attestering.sendTilAttestering
import no.nav.su.se.bakover.web.revurdering.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.revurdering.brevvalg.velgSendBrev
import no.nav.su.se.bakover.web.revurdering.forhåndsvarsel.sendForhåndsvarsel
import no.nav.su.se.bakover.web.revurdering.formue.leggTilFormue
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.innvilgetFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag

internal fun opprettIverksattRevurdering(
    sakid: String,
    fraogmed: String,
    tilogmed: String,
    client: HttpClient,
    appComponents: AppComponents?,
    leggTilUføregrunnlag: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, uføregrad: Int, forventetInntekt: Int, url: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, uføregrad, forventetInntekt, url ->
        leggTilUføregrunnlag(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            uføregrad = uføregrad,
            forventetInntekt = forventetInntekt,
            url = url,
            client = client,
        )
    },
    leggTilBosituasjon: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed ->
        leggTilBosituasjon(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilFormue: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed ->
        leggTilFormue(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    leggTilUtenlandsoppholdRevurdering: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, vurdering: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, vurdering ->
        leggTilUtenlandsoppholdRevurdering(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
            vurdering = vurdering,
        )
    },
    leggTilFlyktningVilkår: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, body: () -> String, url: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, body, url ->
        leggTilFlyktningVilkår(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
            body = body,
            url = url,
        )
    },
    leggTilFradrag: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed ->
        leggTilFradrag(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            client = client,
        )
    },
    beregnOgSimuler: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        beregnOgSimuler(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
    leggTilIngenForhåndsvarsel: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        sendForhåndsvarsel(
            sakId = sakId,
            behandlingId = behandlingId,
            client = client,
        )
    },
    leggTilBrevvalg: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        velgSendBrev(
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
            appComponents = appComponents,
        )
    },
): String {
    return opprettRevurdering(
        sakId = sakid,
        fraOgMed = fraogmed,
        tilOgMed = tilogmed,
        client = client,
    ).let { revurderingJson ->
        val revurderingId = hentRevurderingId(revurderingJson)
        listOf(
            leggTilUføregrunnlag(sakid, revurderingId, fraogmed, tilogmed, 50, 12000, "/saker/$sakid/revurderinger/$revurderingId/uføregrunnlag"),
            leggTilBosituasjon(sakid, revurderingId, fraogmed, tilogmed),
            leggTilFormue(sakid, revurderingId, fraogmed, tilogmed),
            leggTilUtenlandsoppholdRevurdering(sakid, revurderingId, fraogmed, tilogmed, UtenlandsoppholdStatus.SkalHoldeSegINorge.toString()),
            leggTilFlyktningVilkår(sakid, revurderingId, fraogmed, tilogmed, { innvilgetFlyktningVilkårJson(fraogmed, tilogmed) }, "/saker/$sakid/revurderinger/$revurderingId/flyktning"),
            leggTilFradrag(sakid, revurderingId, fraogmed, tilogmed),
            beregnOgSimuler(sakid, revurderingId),
            leggTilIngenForhåndsvarsel(sakid, revurderingId),
            leggTilBrevvalg(sakid, revurderingId),
            sendTilAttestering(sakid, revurderingId),
            iverksett(sakid, revurderingId),
        ).map { it }.last { it != SKIP_STEP }
    }
}
