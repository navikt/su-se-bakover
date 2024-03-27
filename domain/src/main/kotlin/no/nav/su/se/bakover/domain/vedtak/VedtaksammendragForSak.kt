package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.måneder
import java.util.UUID

data class VedtaksammendragForSak(
    val fødselsnummer: Fnr,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val vedtak: List<Vedtak>,
) {
    init {
        require(vedtak.map { it.opprettet }.distinct() == vedtak.map { it.opprettet }) {
            "Vedtakene kan ikke være opprettet på samme tidspunkt"
        }
    }

    /** Disse kan overlappe, så det gir ikke mening og sortere de. */
    val perioder = vedtak.map { it.periode }
    val måneder = perioder.måneder()

    data class Vedtak(
        val opprettet: Tidspunkt,
        val periode: Periode,
        val vedtakstype: Vedtakstype,
    ) {

        init {
            if (vedtakstype == Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE) {
                require(periode.getAntallMåneder() <= 12)
            }
        }
    }

    fun erInnvilgetForMåned(måned: Måned): Boolean = this.tidslinje().forMåned(måned)?.erInnvilget() == true
    fun innvilgetForMåned(måned: Måned): InnvilgetForMåned = this.tidslinje().forMåned(måned).let {
        if (it?.erInnvilget() == true) {
            InnvilgetForMåned(måned, listOf(fødselsnummer))
        } else {
            InnvilgetForMåned(måned, emptyList())
        }
    }

    fun erInnvilgetForMånedEllerSenere(måned: Måned): Boolean =
        this.tidslinje().forMånedEllerSenere(måned).minstEnInnvilget()

    fun innvilgetForMånedEllerSenere(måned: Måned): InnvilgetForMånedEllerSenere =
        this.tidslinje().forMånedEllerSenere(måned).let {
            InnvilgetForMånedEllerSenere(måned, listOf(sakInfo()))
        }

    fun tidslinje(): VedtaksammendragTidslinje {
        return måneder.map { måned ->
            val periodeSomGjelderForDenne =
                this.vedtak.filter { it.periode.overlapper(måned) }
                    .maxByOrNull { it.opprettet.instant }!!

            VedtaksammendragForMåned(
                opprettet = periodeSomGjelderForDenne.opprettet,
                måned = måned,
                fødselsnummer = fødselsnummer,
                vedtakstype = periodeSomGjelderForDenne.vedtakstype,
                sakId = sakId,
                saksnummer = saksnummer,
            )
        }.let {
            VedtaksammendragTidslinje(it.sortedBy { it.måned })
        }
    }

    fun sakInfo(): SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fødselsnummer,
        // TOOD - ALDER
        type = Sakstype.UFØRE,
    )
}

/**
 * Listen kan inneholde vedtak fra ulike saker. Resultatet kan derfor inneholde flere saker.
 */
fun List<VedtaksammendragForSak>.innvilgetForMåned(måned: Måned): InnvilgetForMåned {
    return this.filter {
        it.erInnvilgetForMåned(måned)
    }.let {
        InnvilgetForMåned(måned, it.map { it.fødselsnummer }.sortedBy { it.toString() })
    }
}
