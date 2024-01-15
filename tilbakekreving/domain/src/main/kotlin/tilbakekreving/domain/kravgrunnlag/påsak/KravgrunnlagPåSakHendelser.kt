package tilbakekreving.domain.kravgrunnlag.påsak

import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus

data class KravgrunnlagPåSakHendelser(
    val hendelser: List<KravgrunnlagPåSakHendelse>,
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

    fun hentSisteKravgrunnagforEksternVedtakId(eksternVedtakId: String): Kravgrunnlag? {
        return detaljerSortert
            .filter { it.kravgrunnlag.eksternVedtakId == eksternVedtakId }
            .map { it.kravgrunnlag }
            .maxByOrNull { it.eksternTidspunkt.instant }
            ?.let { kravgrunnlag ->
                hentSisteStatusEtterTidspunkt(kravgrunnlag)?.let {
                    kravgrunnlag.copy(status = it)
                } ?: kravgrunnlag
            }
    }

    /**
     * Henter det siste kravgrunnlaget vi har mottatt og siste status.
     *
     * Merk at vi ikke filtrerer bort kravgrunnlag som allerede er behandlet.
     */
    fun hentSisteKravgrunnlag(): Kravgrunnlag? {
        return detaljerSortert
            .map { it.kravgrunnlag }
            .maxByOrNull { it.eksternTidspunkt.instant }
            ?.let { kravgrunnlag ->
                hentSisteStatusEtterTidspunkt(kravgrunnlag)?.let {
                    kravgrunnlag.copy(status = it)
                } ?: kravgrunnlag
            }
    }

    fun hentKravgrunnlagDetaljerPåSakHendelseForHendelseId(
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
