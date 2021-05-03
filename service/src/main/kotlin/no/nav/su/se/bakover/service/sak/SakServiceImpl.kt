package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SakServiceImpl(
    private val sakRepo: SakRepo,
) : SakService {
    private val log = LoggerFactory.getLogger(this::class.java)
    val observers: MutableList<EventObserver> = mutableListOf()

    override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(sakId)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(fnr)?.right() ?: FantIkkeSak.left()
    }

    override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
        return sakRepo.hentSak(saksnummer)?.right() ?: FantIkkeSak.left()
    }

    override fun opprettSak(sak: NySak) {
        sakRepo.opprettSak(sak).also {
            hentSak(sak.id).fold(
                ifLeft = { log.error("Opprettet sak men feilet ved henting av den.") },
                ifRight = {
                    observers.forEach { observer -> observer.handle(Event.Statistikk.SakOpprettet(it)) }
                },
            )
        }
    }

    override fun hentGrunnlagsdata(fnr: Fnr, periode: Periode): Grunnlagsdata? {
        val sak = sakRepo.hentSak(fnr) ?: return null

        return lagTidslinje(sak.vedtakListe, periode)
            .map { it.behandling.grunnlagsdata }
            .let {
            Grunnlagsdata(
                uføregrunnlag = lagTidslinjeAvGrunnlag(it.flatMap { grunnlag -> grunnlag.uføregrunnlag }, periode),
                flyktninggrunnlag = lagTidslinjeAvGrunnlag(
                    it.flatMap { grunnlag -> grunnlag.flyktninggrunnlag },
                    periode,
                ),
            )
        }
    }

    @JvmName("lagTidslinjeAvGrunnlagsdata1")
    private fun lagTidslinjeAvGrunnlag(grunnlag: List<Grunnlag.Flyktninggrunnlag>, periode: Periode) =
        lagTidslinje(grunnlag, periode).filterIsInstance<Grunnlag.Flyktninggrunnlag>()

    private fun lagTidslinjeAvGrunnlag(grunnlag: List<Grunnlag.Uføregrunnlag>, periode: Periode) =
        lagTidslinje(grunnlag, periode).filterIsInstance<Grunnlag.Uføregrunnlag>()

    private fun <T : KanPlasseresPåTidslinje<T>> lagTidslinje(objekter: List<T>, periode: Periode): List<T> {
        return Tidslinje(periode = periode, objekter = objekter).tidslinje
    }
}
