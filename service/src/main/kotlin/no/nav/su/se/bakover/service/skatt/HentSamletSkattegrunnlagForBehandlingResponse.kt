package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag

data class HentSamletSkattegrunnlagForBehandlingResponse(
    val behandlingensFnr: Fnr,
    val skatteoppslagSøker: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>,
    val skatteoppslagEps: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>?,
)
