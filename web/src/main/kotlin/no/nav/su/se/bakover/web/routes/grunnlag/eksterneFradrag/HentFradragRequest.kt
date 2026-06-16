package no.nav.su.se.bakover.web.routes.grunnlag.eksterneFradrag

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode

data class HentFradragRequest(
    val fnr: Fnr,
    val periode: Periode,
)
