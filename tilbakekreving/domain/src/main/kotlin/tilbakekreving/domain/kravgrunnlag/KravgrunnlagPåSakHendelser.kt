package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.hendelse.domain.HendelseId

data class KravgrunnlagPåSakHendelser(
    private val hendelser: List<KravgrunnlagPåSakHendelse>,
) : List<KravgrunnlagPåSakHendelse> by hendelser {

    val detaljerSortert = hendelser
        .filterIsInstance<KravgrunnlagDetaljerPåSakHendelse>()
        .filter {
            // Dersom revurderingId != null har den blitt behandlet i revurderingen og skal ikke behandles på nytt.
            it.revurderingId == null
        }.sortedBy { it.eksternTidspunkt.instant }

    val statusendringerSortert = hendelser
        .filterIsInstance<KravgrunnlagStatusendringPåSakHendelse>()
        .sortedBy { it.eksternTidspunkt.instant }

    /**
     * TODO - må ta stilling til om den er svart på
     */
    fun hentUteståendeKravgrunnlag(): Kravgrunnlag? {
        return detaljerSortert
            .map { it.kravgrunnlag }
            .maxByOrNull { it.eksternTidspunkt.instant }
            ?.let { kravgrunnlag ->
                hentSisteStatusEtterTidspunkt(kravgrunnlag)?.let {
                    kravgrunnlag.copy(status = it)
                } ?: kravgrunnlag
            }
    }

    fun hentKravgrunnlagDetaljerPåSakHendelseForEksternKravgrunnlagId(
        kravgrunnlagPåSakHendelseId: HendelseId,
    ): KravgrunnlagDetaljerPåSakHendelse? {
        return detaljerSortert
            .singleOrNull { it.hendelseId == kravgrunnlagPåSakHendelseId }
            ?.let { hendelse ->
                hentSisteStatusEtterTidspunkt(hendelse.kravgrunnlag)?.let {
                    hendelse.copy(
                        kravgrunnlag = hendelse.kravgrunnlag.copy(
                            status = it,
                        ),
                    )
                } ?: hendelse
            }
    }

    private fun hentSisteStatusEtterTidspunkt(kravgrunnlag: Kravgrunnlag): Kravgrunnlagstatus? {
        return statusendringerSortert
            .filter { it.eksternVedtakId == kravgrunnlag.eksternVedtakId }
            .lastOrNull { it.eksternTidspunkt > kravgrunnlag.eksternTidspunkt }
            ?.status
    }
}
