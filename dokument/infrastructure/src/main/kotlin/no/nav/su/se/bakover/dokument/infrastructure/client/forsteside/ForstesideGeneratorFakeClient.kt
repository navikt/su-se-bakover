package no.nav.su.se.bakover.dokument.infrastructure.client.forsteside

import arrow.core.Either
import arrow.core.right
import dokument.domain.forsteside.ForstesideGeneratorClient
import dokument.domain.forsteside.KunneIkkeGenerereForsteside
import dokument.domain.forsteside.PostForstesideRequest
import dokument.domain.forsteside.PostForstesideResponse

class ForstesideGeneratorFakeClient : ForstesideGeneratorClient {

    private val pdfBytes = javaClass.classLoader.getResourceAsStream("Foersteside.pdf")!!.readAllBytes()
    private val pdfBytesAlder = javaClass.classLoader.getResourceAsStream("FoerstesideSoknad.pdf")!!.readAllBytes()

    override fun genererForsteside(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> =
        if (request.navSkjemaId == "NAV 00-03.01") {
            PostForstesideResponse(
                foersteside = pdfBytes,
                løpenummer = "mock-løpenummer",
            ).right()
        } else if (request.navSkjemaId == "NAV 64-21.00") {
            PostForstesideResponse(
                foersteside = pdfBytesAlder,
                løpenummer = "mock-løpenummer",
            ).right()
        } else {
            throw IllegalArgumentException("Ukjent navSkjemaId: ${request.navSkjemaId}")
        }
}
