package no.nav.su.se.bakover.web.revurdering

import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.revurdering.attestering.sendTilAttestering
import no.nav.su.se.bakover.web.revurdering.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.revurdering.forhåndsvarsel.leggTilIngenForhåndsvarsel
import no.nav.su.se.bakover.web.revurdering.formue.leggTilFormue
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.innvilgetFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag

internal fun ApplicationTestBuilder.opprettIverksattRevurdering(
    sakid: String,
    fraogmed: String,
    tilogmed: String,
    leggTilUføregrunnlag: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, uføregrad: Int, forventetInntekt: Int, url: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, uføregrad, forventetInntekt, url ->
        leggTilUføregrunnlag(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            uføregrad = uføregrad,
            forventetInntekt = forventetInntekt,
            url = url,
        )
    },
    leggTilBosituasjon: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed ->
        leggTilBosituasjon(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        )
    },
    leggTilFormue: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed ->
        leggTilFormue(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        )
    },
    leggTilUtenlandsoppholdRevurdering: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, vurdering: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, vurdering ->
        leggTilUtenlandsoppholdRevurdering(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vurdering = vurdering,
        )
    },
    leggTilFlyktningVilkår: (sakId: String, behandlingId: String, fraOgMed: String, tilOgMed: String, body: () -> String, url: String) -> String = { sakId, behandlingId, fraOgMed, tilOgMed, body, url ->
        leggTilFlyktningVilkår(
            sakId = sakId,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
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
        )
    },
    beregnOgSimuler: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        beregnOgSimuler(
            sakId = sakId,
            behandlingId = behandlingId,
        )
    },
    leggTilIngenForhåndsvarsel: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        leggTilIngenForhåndsvarsel(
            sakId = sakId,
            behandlingId = behandlingId,
        )
    },
    sendTilAttestering: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        sendTilAttestering(
            sakId = sakId,
            behandlingId = behandlingId,
        )
    },
    iverksett: (sakId: String, behandlingId: String) -> String = { sakId, behandlingId ->
        iverksett(
            sakId = sakId,
            behandlingId = behandlingId,
        )
    },
): String {
    return opprettRevurdering(
        sakId = sakid,
        fraOgMed = fraogmed,
        tilOgMed = tilogmed,
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
            sendTilAttestering(sakid, revurderingId),
            iverksett(sakid, revurderingId),
        ).map { it }.last { it != SKIP_STEP }
    }
}
