package dokument.domain.forsteside

import arrow.core.Either

interface ForstesideGeneratorClient {
    fun genererForsteside(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse>
}
data object KunneIkkeGenerereForsteside
