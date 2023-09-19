package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.left
import org.slf4j.LoggerFactory
import tilbakekreving.domain.hent.KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.util.UUID

class HentÅpentKravgrunnlagService(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun hentÅpentKravgrunnlag(
        sakId: UUID,
        kravgrunnlagMapper: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    ): Either<KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag, Kravgrunnlag> {
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.IkkeTilgang(it).left()
        }

        return kravgrunnlagRepo.hentÅpentKravgrunnlagForSak(sakId)?.let {
            kravgrunnlagMapper(it).map {
                it
            }.mapLeft {
                log.error("Feil ved mapping av kravgrunnlag på sak $sakId")
                KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FeilVedMappingAvKravgrunnalget
            }
        } ?: KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag.FinnesIngenFerdigBehandledeKravgrunnlag.left()
    }
}
