package dokument.domain.forsteside

import arrow.core.Either

interface ForstesideGeneratorClient {
    fun genererForstesideKontrollnotat(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse>

    fun genererForstesideSøknadAlder(
        request: PostForstesideRequest,
    ): Either<KunneIkkeGenerereForsteside, PostForstesideResponse>
}
data object KunneIkkeGenerereForsteside
