package no.nav.su.se.bakover.service.kontrollsamtale

import no.nav.su.se.bakover.common.førsteINesteMåned
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.sak.SakService
import java.time.LocalDate

interface UtløptFristForKontrollsamtaleService {
    fun håndterKontrollsamtalerMedFristUtløpt(dato: LocalDate)
}

internal class UtløptFristForKontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val journalpostClient: JournalpostClient,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val revurderingService: RevurderingService,
) : UtløptFristForKontrollsamtaleService {
    override fun håndterKontrollsamtalerMedFristUtløpt(dato: LocalDate) {
        kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløpt(dato).forEach {
            prosesser(it)
        }
    }

    private fun prosesser(kontrollsamtale: Kontrollsamtale) {
        return sakService.hentSakInfo(kontrollsamtale.sakId)
            .fold(
                {
                    log.error("Fant ikke sakinfo for sakId:${kontrollsamtale.sakId}")
                },
                { sakInfo ->
                    journalpostClient.kontrollnotatMotatt(
                        saksnummer = sakInfo.saksnummer,
                        periode = Periode.create(
                            fraOgMed = kontrollsamtale.innkallingsdato,
                            tilOgMed = kontrollsamtale.frist
                        )
                    ).fold(
                        {
                            log.error("Feil: $it ved henting av dokumenter for ${kontrollsamtale.sakId}")
                        },
                        {
                            when (it) {
                                is ErKontrollNotatMottatt.Ja -> {
                                    kontrollnotatMottatt(
                                        kontrollsamtale = kontrollsamtale,
                                        journalpostId = JournalpostId("$it"), // TODO koble mot journalpostid
                                    )
                                }
                                is ErKontrollNotatMottatt.Nei -> {
                                    kontrollnotatIkkeMottatt(
                                        kontrollsamtale = kontrollsamtale
                                    )
                                }
                            }
                        },
                    )
                },
            )
    }

    private fun kontrollnotatMottatt(kontrollsamtale: Kontrollsamtale, journalpostId: JournalpostId) {
        return kontrollsamtale.settGjennomført(journalpostId = journalpostId).fold(
            {
                log.error("Feil: $it ved oppdatering av gjennomført kontrollsamtale: $it, kontrollnotat:$journalpostId")
            },
            {
                kontrollsamtaleRepo.lagre(it)
            },
        )
    }

    private fun kontrollnotatIkkeMottatt(kontrollsamtale: Kontrollsamtale) {
        return revurderingService.stansAvYtelse(
            request = StansYtelseRequest.Opprett(
                sakId = kontrollsamtale.sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("srvsupstonad"),
                fraOgMed = kontrollsamtale.frist.førsteINesteMåned(),
                revurderingsårsak = Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("Automatisk stans")
                )
            )
        ).fold(
            {
                log.error("Kunne ikke stanse ytelse for sakId: ${kontrollsamtale.sakId}, feil: $it")
            },
            { simulertStans ->
                revurderingService.iverksettStansAvYtelse(
                    revurderingId = simulertStans.id,
                    attestant = NavIdentBruker.Attestant("srvsupstonad")
                ).fold(
                    {
                        log.error("Kunne ikke stanse ytelse for sakId: ${kontrollsamtale.sakId}, feil: $it")
                    },
                    {
                    }
                )
            }
        )
    }
}
