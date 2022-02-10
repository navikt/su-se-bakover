package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.time.Clock

internal class TilbakekrevingConsumer(
    private val tilbakekrevingService: TilbakekrevingService,
    private val sakService: SakService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun onMessage(xmlMessage: String) {
        val mottattKravgrunnlag = KravgrunnlagMapper.toDto(xmlMessage)
            .getOrHandle { throw it }
        val mottattSaksnummer = Saksnummer(mottattKravgrunnlag.fagsystemId.toLong())
        // TODO kan unngås med henvisning/referanse
        val sakIdOgNummer = sakService.hentSakidOgSaksnummer(Fnr(mottattKravgrunnlag.vedtakGjelderId))
            .getOrHandle { throw IllegalStateException("Fant ikke sak for saksnummer:$mottattSaksnummer") }

        val tilbakekrevingsbehandling =
            tilbakekrevingService.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakIdOgNummer.sakId)
                .singleOrNull()
            // TODO midlertidig løsning til henvisning/referanser send med utbetaling er på plass
                ?: throw IllegalStateException("Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for sakId: ${sakIdOgNummer.sakId}, sak:$mottattSaksnummer, men fant ingen eller flere")

        tilbakekrevingsbehandling.mottattKravgrunnlag(
            kravgrunnlag = RåttKravgrunnlag(xmlMelding = xmlMessage),
            kravgrunnlagMottatt = Tidspunkt.now(clock),
        ).let {
            tilbakekrevingService.lagreMottattKravgrunnlag(it)
            log.info("Mottatt kravgrunnlag for tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id} for revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}")
        }
    }
}
