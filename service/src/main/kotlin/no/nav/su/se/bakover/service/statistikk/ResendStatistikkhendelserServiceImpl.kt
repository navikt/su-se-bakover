package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Har som ansvar og resende hendelser som ikke har blitt sendt til statistikk.
 */
class ResendStatistikkhendelserServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val sakRepo: SakRepo,
    private val statistikkEventObserver: StatistikkEventObserver,
) : ResendStatistikkhendelserService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun resendIverksattSøknadsbehandling(
        fraOgMedDato: LocalDate,
    ) {
        log.info("Resend statistikk: Starter resend av statistikk for søknadsbehandlingvedtak fra og med $fraOgMedDato.")
        vedtakRepo.hentSøknadsbehandlingsvedtakFraOgMed(fraOgMedDato).also {
            log.info("Resend statistikk: Fant ${it.size} søknadsbehandlingvedtak fra og med $fraOgMedDato som skal resendes.")
        }.forEachIndexed { index, vedtakId ->

            log.info("Resend statistikk: for søknadsbehandlingvedtak $vedtakId")
            sakRepo.hentSakForVedtak(vedtakId).let { sak ->
                val vedtak = Either.catch {
                    sak!!.vedtakListe.single { it.id == vedtakId } as VedtakIverksattSøknadsbehandling
                }.getOrElse {
                    log.error(
                        "Resend statistikk: Fant ikke sak for vedtak ($vedtakId) eller var ikke av type VedtakIverksattSøknadsbehandling. FraOgMedDato: $fraOgMedDato",
                        it,
                    )
                    if (index == 0) {
                        log.error("Resend statistikk: Siden vi feilet på første element, avbryter vi. FraOgMedDato: $fraOgMedDato, vedtakId:$vedtakId")
                        return
                    }
                    return@forEachIndexed
                }
                when (vedtak) {
                    is VedtakInnvilgetSøknadsbehandling -> {
                        statistikkEventObserver.handle(StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak))
                        statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                        log.info("Resend statistikk: Sendte statistikk for VedtakInnvilgetSøknadsbehandling $vedtakId. FraOgMedDato: $fraOgMedDato.")
                    }

                    is Avslagsvedtak -> {
                        statistikkEventObserver.handle(StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak))
                        log.info("Resend statistikk: Sendte statistikk for Avslagsvedtak $vedtakId. FraOgMedDato: $fraOgMedDato")
                    }
                }
            }
        }
    }
}
