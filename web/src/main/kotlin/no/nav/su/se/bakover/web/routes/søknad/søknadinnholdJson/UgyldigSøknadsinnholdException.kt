package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

internal class UgyldigSøknadsinnholdException(
    val felt: String,
    val begrunnelse: String,
) : RuntimeException("Ugyldig søknadsinnhold i felt $felt: $begrunnelse")
