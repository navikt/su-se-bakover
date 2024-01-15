package tilbakekreving.infrastructure.repo

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagOgIverksatteTilbakekrevingerRepo
import tilbakekreving.infrastructure.repo.iverksatt.mapToTilIverksattHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.KnyttetKravgrunnlagTilSakHendelsestype
import tilbakekreving.infrastructure.repo.kravgrunnlag.toKravgrunnlagPåSakHendelse

class KravgrunnlagOgIverksatteTilbakekrevingerPostgresRepo(
    private val dbMetrics: DbMetrics,
    val sessionFactory: SessionFactory,
) : KravgrunnlagOgIverksatteTilbakekrevingerRepo {

    override fun hentKravgrunnlagOgIverksatteTilbakekrevinger(
        sessionContext: SessionContext?,
    ): Pair<List<KravgrunnlagPåSakHendelse>, List<IverksattHendelse>> {
        return dbMetrics.timeQuery("hentKravgrunnlagOgIverksatteTilbakekrevinger") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                    select hendelseId, data, hendelsestidspunkt, versjon, type, sakId, tidligereHendelseId, entitetId
                    from hendelse
                    where type IN ('$KnyttetKravgrunnlagTilSakHendelsestype','$IverksattTilbakekrevingsbehandlingHendelsestype')
                """.trimIndent().hentListe(
                    session = session,
                ) { HendelsePostgresRepo.toPersistertHendelse(it) }.map {
                    when (it.type) {
                        IverksattTilbakekrevingsbehandlingHendelsestype -> it.mapToTilIverksattHendelse()
                        KnyttetKravgrunnlagTilSakHendelsestype -> it.toKravgrunnlagPåSakHendelse()
                        else -> throw IllegalStateException("Uforventet hendelsestype i resultatsettet: ${it.type}")
                    }
                }.let {
                    Pair(
                        it.filterIsInstance<KravgrunnlagPåSakHendelse>(),
                        it.filterIsInstance<IverksattHendelse>(),
                    )
                }
            }
        }
    }
}
