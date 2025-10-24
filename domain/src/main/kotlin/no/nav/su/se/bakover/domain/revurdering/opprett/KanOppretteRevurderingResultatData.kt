package no.nav.su.se.bakover.domain.revurdering.opprett

import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vedtak.domain.VedtakSomKanRevurderes

data class KanOppretteRevurderingResultatData(
    val gjeldendeVedtak: VedtakSomKanRevurderes,
    val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    val revurderingsårsak: Revurderingsårsak,
    val informasjonSomRevurderes: InformasjonSomRevurderes,
    val klageId: KlageId? = null,
)
