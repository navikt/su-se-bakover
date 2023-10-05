package tilbakekreving.domain.kravgrunnlag

data class KravgrunnlagPåSakHendelser(
    val hendelser: List<KravgrunnlagPåSakHendelse>,
) : List<KravgrunnlagPåSakHendelse> by hendelser {

    fun hentUteståendeKravgrunnlag(): Kravgrunnlag? {
        return hendelser
            .map { it.kravgrunnlag }
            .maxByOrNull { it.eksternTidspunkt.instant }
    }
}
