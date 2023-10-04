package tilbakekreving.application.service

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.time.Clock

class RåttKravgrunnlagService(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val clock: Clock,
) {
    /**
     * Vi har ikke noe de-dup i dette leddet, så vi kan få "duplikat"-hendelser i databasen.
     * Vi løser de-dup i neste ledd, når vi knytter den mot en utbetaling på en sak.
     */
    fun lagreRåKvitteringshendelse(
        råttKravgrunnlag: RåttKravgrunnlag,
        meta: JMSHendelseMetadata,
    ) {
        kravgrunnlagRepo.lagreRåttKravgrunnlagHendelse(
            hendelse = RåttKravgrunnlagHendelse(
                hendelseId = HendelseId.generer(),
                hendelsestidspunkt = Tidspunkt.now(clock),
                meta = meta,
                råttKravgrunnlag = råttKravgrunnlag,
            ),
        )
    }
}
