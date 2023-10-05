package tilbakekreving.domain.kravgrunnlag

data class KravgrunnlagPåSakHendelser(
    val hendelser: List<KravgrunnlagPåSakHendelse>,
) : List<KravgrunnlagPåSakHendelse> by hendelser {

    fun hentUteståendeKravgrunnlag(): Kravgrunnlag? {
        return hendelser
            .filter {
                // Dersom revurderingId != null har den blitt behandlet i revurderingen og skal ikke behandles på nytt.
                it.revurderingId == null
            }
            .map { it.kravgrunnlag }
            .maxByOrNull { it.eksternTidspunkt.instant }
    }
}
