package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

fun nyFradragsgrunnlag(
    opprettet: Tidspunkt = fixedTidspunkt,
    type: Fradragstype = Fradragstype.Kontantstøtte,
    månedsbeløp: Double = 200.0,
    periode: Periode = år(2021),
    utenlandskInntekt: UtenlandskInntekt? = null,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
): Fradragsgrunnlag = Fradragsgrunnlag.create(
    opprettet = opprettet,
    fradrag = FradragFactory.nyFradragsperiode(
        fradragstype = type,
        månedsbeløp = månedsbeløp,
        periode = periode,
        utenlandskInntekt = utenlandskInntekt,
        tilhører = tilhører,
    ),
)
