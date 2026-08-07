package dokument.domain.forsteside

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.kodeverk.Tema

class ForstesideGeneratorService(
    private val forstesideGeneratorClient: ForstesideGeneratorClient,
) {
    fun genererForKontrollnotat(
        brukerId: String,
        behandlingstema: String,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val request = PostForstesideRequest(
            netsPostboks = "1402",
            bruker = Bruker(
                brukerId = brukerId,
                brukerType = Brukertype.PERSON,
            ),
            tema = Tema.SUPPLERENDE_STØNAD.value,
            arkivtittel = "NAV SU Kontrollnotat",
            overskriftstittel = "NAV 00-03.01 NAV SU Kontrollnotat ($brukerId)",
            foerstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = "NAV 00-03.01",
            behandlingstema = behandlingstema,
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForstesideKontrollnotat(request)
    }

    fun genererForSøknadAlder(
        brukerId: String,
        behandlingstema: String,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val request = PostForstesideRequest(
            netsPostboks = "1402",
            bruker = Bruker(
                brukerId = brukerId,
                brukerType = Brukertype.PERSON,
            ),
            tema = Tema.SUPPLERENDE_STØNAD.value,
            arkivtittel = "Søknad om supplerende stønad til personer med kort botid i Norge",
            overskriftstittel = "NAV 64-21.00 Søknad om supplerende stønad til personer med kort botid i Norge ($brukerId)",
            foerstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = "NAV 64-21.00",
            behandlingstema = behandlingstema,
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForstesideSøknadAlder(request)
    }
}
