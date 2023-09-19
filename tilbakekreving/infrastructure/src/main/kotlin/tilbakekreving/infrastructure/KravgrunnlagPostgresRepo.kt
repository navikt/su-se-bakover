package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.util.UUID

class KravgrunnlagPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) : KravgrunnlagRepo {
    override fun hentÅpentKravgrunnlagForSak(sakId: UUID): RåttKravgrunnlag? {
        // TODO jah: MOTTATT_KRAVGRUNNLAG er en delt tilstand, men vil på sikt eies av kravgrunnlag-delen av tilbakekreving.
        // TODO jah: Vi skal flytte fremtidige kravgrunnlag til en egen hendelse.
        return sessionFactory.withSession { session ->
            """
                select 
                    kravgrunnlag 
                from 
                    revurdering_tilbakekreving 
                where 
                    tilstand = 'MOTTATT_KRAVGRUNNLAG' 
                    and tilbakekrevingsvedtakForsendelse is null 
                    and sakId=:sakId
                    and opprettet = (SELECT MAX(opprettet) FROM revurdering_tilbakekreving);
            """.trimIndent()
                .hent(
                    mapOf("sakId" to sakId),
                    session,
                ) {
                    it.stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
                }
        }
    }
}
