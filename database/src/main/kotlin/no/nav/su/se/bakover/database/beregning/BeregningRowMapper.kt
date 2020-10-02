package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal.hentFradrag
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal.hentMånedsberegninger
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.InntektDelerAvPeriode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.UtenlandskInntekt

internal fun Row.toBeregning(session: Session) = Beregning(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = localDate("fom"),
    tilOgMed = localDate("tom"),
    sats = Sats.valueOf(string("sats")),
    månedsberegninger = hentMånedsberegninger(uuid("id"), session),
    fradrag = hentFradrag(uuid("id"), session),
    forventetInntekt = int("forventetInntekt")
)

internal fun Row.toMånedsberegning() = Månedsberegning(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = localDate("fom"),
    tilOgMed = localDate("tom"),
    grunnbeløp = int("grunnbeløp"),
    sats = Sats.valueOf(string("sats")),
    beløp = int("beløp"),
    fradrag = int("fradrag")
)

internal fun Row.toFradrag() = Fradrag(
    id = uuid("id"),
    beløp = int("beløp"),
    type = Fradragstype.valueOf(string("fradragstype")),
    utenlandskInntekt = objectMapper.readValue(string("utenlandskInntekt")) as UtenlandskInntekt?,
    inntektDelerAvPeriode = objectMapper.readValue(string("inntektDelerAvPeriode")) as InntektDelerAvPeriode?,
)
