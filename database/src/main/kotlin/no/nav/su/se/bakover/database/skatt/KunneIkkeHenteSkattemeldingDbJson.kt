package no.nav.su.se.bakover.database.skatt

import vilkår.skatt.domain.KunneIkkeHenteSkattemelding

internal fun KunneIkkeHenteSkattemelding.toDbJson(): StadieJson = when (this) {
    is KunneIkkeHenteSkattemelding.FinnesIkke -> StadieJson.FinnesIkke

    KunneIkkeHenteSkattemelding.ManglerRettigheter -> StadieJson.ManglerRettigheter
    is KunneIkkeHenteSkattemelding.Nettverksfeil -> StadieJson.Nettverksfeil
    is KunneIkkeHenteSkattemelding.UkjentFeil -> StadieJson.UkjentFeil
    KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData -> StadieJson.OppsalgetInneholdtUgyldigData
}
