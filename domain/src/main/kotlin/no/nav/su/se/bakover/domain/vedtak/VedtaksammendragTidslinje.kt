package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.common.tid.periode.Måned

/**
 * Akespterer hull i tidslinje
 */
data class VedtaksammendragTidslinje(
    private val vedtaksammendrag: List<VedtaksammendragForMåned>,
) : List<VedtaksammendragForMåned> by vedtaksammendrag {
    init {
        vedtaksammendrag.map { it.måned }.let {
            require(it.sorted() == it) { "Månedene i vedtaksammendragForPeriode er ikke sortert" }
        }
        require(vedtaksammendrag.distinctBy { it.måned } == vedtaksammendrag) {
            "VedtaksammendragTidslinje inneholder duplikate måneder"
        }
        require(vedtaksammendrag.map { it.sakId }.distinct().size == 1) {
            // TODO jah: Denne blir feil hvis vi implementerer alder
            "Alle vedtaksammendrag må være knyttet til samme sakId"
        }
        require(vedtaksammendrag.map { it.saksnummer }.distinct().size == 1) {
            // TODO jah: Denne blir feil hvis vi implementerer alder
            "Alle vedtaksammendrag må være knyttet til samme saksnummer"
        }
        require(vedtaksammendrag.map { it.fødselsnummer }.distinct().size == 1) {
            // TODO jah: Denne blir feil hvis vi implementerer alder
            "Alle vedtaksammendrag må være knyttet til samme fødselsnummer"
        }
    }

    val fnr = vedtaksammendrag.firstOrNull()?.fødselsnummer

    fun forMåned(måned: Måned): VedtaksammendragForMåned? = this.singleOrNull { it.måned == måned }

    fun forMånedEllerSenere(fraOgMedEllerSenere: Måned): VedtaksammendragTidslinje =
        VedtaksammendragTidslinje(this.filter { it.måned >= fraOgMedEllerSenere }.sortedBy { it.måned })

    fun minstEnInnvilget(): Boolean = this.vedtaksammendrag.any { it.erInnvilget() }

    fun sakInfo(): SakInfo? = this.whenever(
        isEmpty = { null },
        isNotEmpty = {
            SakInfo(
                sakId = vedtaksammendrag.first().sakId,
                saksnummer = vedtaksammendrag.first().saksnummer,
                fnr = this.first().fødselsnummer,
                // TODO - ALDER
                type = Sakstype.UFØRE,
            )
        },
    )
}
