package no.nav.su.se.bakover.service.sak

import arrow.core.Either
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.domain.sak.BegrensetSakinfo
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.Sak
import no.nav.su.se.bakover.domain.sak.SakIdOgNummer
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.util.UUID

interface SakService {
    fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak>
    fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak>
    fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak>
    fun opprettSak(sak: NySak)
    fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingsoversikt>
    fun hentBegrensetSakinfo(fnr: Fnr): Either<FantIkkeSak, BegrensetSakinfo>
    fun hentSakidOgSaksnummer(fnr: Fnr): Either<FantIkkeSak, SakIdOgNummer>
}

object FantIkkeSak
