package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import vilkår.inntekt.domain.grunnlag.Fradragstype

data class ReguleringSomKreverManuellBehandling(
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val reguleringId: ReguleringId,
    val fradragsKategori: List<Fradragstype.Kategori>,
    val årsakTilManuellRegulering: List<ÅrsakTilManuellReguleringKategori>,
    val status: String,
    val sisteVedtakType: String,
    val sisteVedtakOpprettet: Tidspunkt,
)
