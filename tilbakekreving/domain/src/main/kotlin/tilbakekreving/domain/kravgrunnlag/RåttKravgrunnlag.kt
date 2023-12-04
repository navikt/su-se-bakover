package tilbakekreving.domain.kravgrunnlag

@JvmInline
value class RåttKravgrunnlag private constructor(
    val melding: String,
) {
    companion object {
        operator fun invoke(
            xmlMelding: String,
        ): RåttKravgrunnlag {
            return RåttKravgrunnlag(xmlMelding)
        }
    }
}
