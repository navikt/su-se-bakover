package dokument.domain.forsteside

import arrow.core.Either
import dokument.domain.journalføring.tilBehandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype

class ForstesideGeneratorService(
    private val forstesideGeneratorClient: ForstesideGeneratorClient,
) {
    fun genererForKontrollnotat(
        brukerId: String,
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
            navSkjemaId = KONTROLLNOTAT,
            enhetsnummer = "4815",
            behandlingstema = null,
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }

    fun genererForSøknadAlder(
        brukerId: String,
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
            navSkjemaId = SØKNAD_ALDER,
            behandlingstema = Sakstype.ALDER.tilBehandlingstema(),
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }

    fun genererForSøknadUføre(
        brukerId: String,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val request = PostForstesideRequest(
            netsPostboks = "1402",
            bruker = Bruker(
                brukerId = brukerId,
                brukerType = Brukertype.PERSON,
            ),
            tema = Tema.SUPPLERENDE_STØNAD.value,
            arkivtittel = "Søknad om supplerende stønad til ufør flyktning",
            overskriftstittel = "NAV 64-01.00 Søknad om supplerende stønad til ufør flyktning ($brukerId)",
            foerstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = SØKNAD_UFØRE,
            behandlingstema = Sakstype.UFØRE.tilBehandlingstema(),
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }

    fun genererForsideForEttersendingAlder(
        brukerId: String,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val request = PostForstesideRequest(
            netsPostboks = "1402",
            bruker = Bruker(
                brukerId = brukerId,
                brukerType = Brukertype.PERSON,
            ),
            tema = Tema.SUPPLERENDE_STØNAD.value,
            arkivtittel = "Ettersendelse til Søknad om supplerende stønad til personer med kort botid i Norge",
            overskriftstittel = "NAVe 64-21.00 Ettersendelse til Søknad om supplerende stønad til personer med kort botid i Norge ($brukerId)",
            foerstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = SØKNAD_ETTERSENDELSE_ALDER,
            behandlingstema = Sakstype.ALDER.tilBehandlingstema(),
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }

    fun genererForsideForEttersendingUføre(
        brukerId: String,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> {
        val request = PostForstesideRequest(
            netsPostboks = "1402",
            bruker = Bruker(
                brukerId = brukerId,
                brukerType = Brukertype.PERSON,
            ),
            tema = Tema.SUPPLERENDE_STØNAD.value,
            arkivtittel = "Ettersendelse til Søknad om supplerende stønad til ufør flyktning",
            overskriftstittel = "NAVe 64-01.00 Ettersendelse til Søknad om supplerende stønad til ufør flyktning ($brukerId)",
            foerstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = SØKNAD_ETTERSENDELSE_UFØRE,
            behandlingstema = Sakstype.UFØRE.tilBehandlingstema(),
            enhetsnummer = "4815",
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }
}
