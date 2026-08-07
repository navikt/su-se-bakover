package no.nav.su.se.bakover.dokument.infrastructure.client.forsteside

import arrow.core.Either
import arrow.core.right
import dokument.domain.forsteside.ForstesideGeneratorClient
import dokument.domain.forsteside.KunneIkkeGenerereForsteside
import dokument.domain.forsteside.PostForstesideRequest
import dokument.domain.forsteside.PostForstesideResponse
import dokument.domain.forsteside.hentFakePdfForSkjema

class ForstesideGeneratorFakeClient : ForstesideGeneratorClient {

    private val pdfBytes = javaClass.classLoader.getResourceAsStream("Foersteside.pdf")!!.readAllBytes()
    private val pdfBytesAlder = javaClass.classLoader.getResourceAsStream("FoerstesideSoknad.pdf")!!.readAllBytes()

    override fun genererForsteside(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> =
        PostForstesideResponse(
            foersteside = hentFakePdfForSkjema(
                skjemaId = request.navSkjemaId,
                kontrollnotatPdf = pdfBytes,
                alderPdf = pdfBytesAlder,
            ),
            løpenummer = "mock-løpenummer",
        ).right()
}
