package no.nav.su.se.bakover.dokument.infrastructure.client.forsteside

import arrow.core.Either
import arrow.core.right
import dokument.domain.forsteside.ForstesideGeneratorClient
import dokument.domain.forsteside.KunneIkkeGenerereForsteside
import dokument.domain.forsteside.PostForstesideRequest
import dokument.domain.forsteside.PostForstesideResponse
import dokument.domain.forsteside.hentFakePdfForSkjema

class ForstesideGeneratorFakeClient : ForstesideGeneratorClient {

    private val pdfBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("Foersteside.pdf")).use { it.readAllBytes() }
    private val pdfBytesAlder = requireNotNull(javaClass.classLoader.getResourceAsStream("FoerstesideSoknad.pdf")).use { it.readAllBytes() }
    private val pdfBytesUfore = requireNotNull(javaClass.classLoader.getResourceAsStream("FoerstesideSoknadUfor.pdf")).use { it.readAllBytes() }

    override fun genererForsteside(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse> =
        PostForstesideResponse(
            foersteside = hentFakePdfForSkjema(
                skjemaId = request.navSkjemaId,
                kontrollnotatPdf = pdfBytes,
                alderPdf = pdfBytesAlder,
                uførePdf = pdfBytesUfore,
            ),
            løpenummer = "mock-løpenummer",
        ).right()
}
