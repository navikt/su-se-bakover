import arrow.core.Either

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
            tema = "SUP",
            arkivtittel = "NAV SU Kontrollnotat",
            overskriftstittel = "NAV 00-03.01 NAV SU Kontrollnotat ($brukerId)",
            forstesidetype = Forstesidetype.NAV_INTERN,
            navSkjemaId = "NAV 00-03.01",
        )
        return forstesideGeneratorClient.genererForsteside(request)
    }
}
