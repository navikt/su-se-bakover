package dokument.domain.forsteside

data class PostForstesideRequest(
    val netsPostboks: String,
    val bruker: Bruker,
    val tema: String,
    val arkivtittel: String,
    val overskriftstittel: String,
    val foerstesidetype: Forstesidetype,
    val navSkjemaId: String,
    val behandlingstema: String,
    val enhetsnummer: String,
)
