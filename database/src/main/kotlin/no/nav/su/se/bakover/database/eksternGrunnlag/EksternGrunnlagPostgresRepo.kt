package no.nav.su.se.bakover.database.eksternGrunnlag

import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.database.skatt.SkattPostgresRepo
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import java.util.UUID

internal class EksternGrunnlagPostgresRepo(
    private val skattRepo: SkattPostgresRepo,
) {

    fun lagre(sakId: UUID, eksternegrunnlag: EksterneGrunnlag, session: TransactionalSession) {
        skattRepo.lagre(sakId, eksternegrunnlag.skatt, session)
    }

    fun hentSkattegrunnlag(søkersId: UUID?, epsId: UUID?, session: Session): EksterneGrunnlagSkatt {
        val søkers = søkersId?.let { skattRepo.hent(it, session) }
        val eps = epsId?.let { skattRepo.hent(it, session) }

        return when (søkersId) {
            null -> EksterneGrunnlagSkatt.IkkeHentet
            else -> EksterneGrunnlagSkatt.Hentet(
                søkers = søkers!!,
                eps = eps,
            )
        }
    }

    fun slettEksisterende(eksisterendeReferanser: IdReferanser, oppdaterteReferanser: IdReferanser, session: Session) {
        if (eksisterendeReferanser.skattereferanser != null && oppdaterteReferanser.skattereferanser != null) {
            skattRepo.slettEksisterende(
                eksisterendeReferanser.skattereferanser,
                oppdaterteReferanser.skattereferanser,
                session,
            )
        }
    }
}
