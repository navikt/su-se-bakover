package no.nav.su.se.bakover.web.revurdering

import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.revurdering.attestering.sendTilAttestering
import no.nav.su.se.bakover.web.revurdering.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.revurdering.forhåndsvarsel.leggTilIngenForhåndsvarsel
import no.nav.su.se.bakover.web.revurdering.formue.leggTilFormue
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.iverksett.iverksett
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.innvilgetFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.uførhet.leggTilUføregrunnlag

internal fun ApplicationTestBuilder.opprettIverksattRevurdering(
    sakId: String,
    fraOgMed: String,
    tilOgMed: String,
    utenlandsOpphold: UtenlandsoppholdStatus = UtenlandsoppholdStatus.SkalHoldeSegINorge,
): String {
    return opprettRevurdering(
        sakId = sakId,
        fraOgMed = fraOgMed,
    ).let {
        val revurderingId = hentRevurderingId(it)

        leggTilUføregrunnlag(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            uføregrad = 50,
            forventetInntekt = 12000,
            url = "/saker/$sakId/revurderinger/$revurderingId/uføregrunnlag",
        )
        leggTilBosituasjon(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        )
        leggTilFormue(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        )
        leggTilUtenlandsoppholdRevurdering(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vurdering = utenlandsOpphold.toString(),
        )
        leggTilFlyktningVilkår(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            body = { innvilgetFlyktningVilkårJson(fraOgMed, tilOgMed) },
            url = "/saker/$sakId/revurderinger/$revurderingId/flyktning",
        )
        leggTilFradrag(
            sakId = sakId,
            behandlingId = revurderingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
        )
        beregnOgSimuler(
            sakId = sakId,
            behandlingId = revurderingId,
        )
        leggTilIngenForhåndsvarsel(
            sakId = sakId,
            behandlingId = revurderingId,
        )
        sendTilAttestering(
            sakId = sakId,
            behandlingId = revurderingId,
        )
        iverksett(
            sakId = sakId,
            behandlingId = revurderingId,
        )
    }
}
