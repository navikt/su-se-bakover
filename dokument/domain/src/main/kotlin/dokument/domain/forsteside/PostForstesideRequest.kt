package dokument.domain.forsteside

data class PostForstesideRequest(
    val netsPostboks: String,
    val bruker: Bruker,
    val tema: String,
    val arkivtittel: String,
    val overskriftstittel: String,
    val forstesidetype: Forstesidetype,
    val navSkjemaId: String,
)
