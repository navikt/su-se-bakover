package no.nav.su.se.bakover.service.statistikk

import behandling.domain.Behandling
import behandling.domain.Stønadsbehandling
import no.nav.su.se.bakover.common.SikkerLogg
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.Kode6Vurdering
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import person.domain.Adressebeskyttelse
import person.domain.PersonOppslag
import person.domain.PersonerOgSakstype
import toBehandlingsstatistikkOverordnet
import vedtak.domain.Stønadsvedtak
import java.time.Clock
import java.util.UUID

class SakStatistikkService(
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
    private val personOppslag: PersonOppslag,
    private val sakRepo: SakRepo,
    private val sikkerLogg: SikkerLogg = no.nav.su.se.bakover.common.sikkerLogg,
    private val hentHarKode6Gruppe: () -> Boolean = {
        requireNotNull(Kontekst.get()) { "Mangler brukerkontekst for sakstatistikk" }.harKode6Gruppe
    },
) {
    fun vurderForBruker(
        personer: PersonerOgSakstype,
    ): Kode6Vurdering {
        require(personer.fnr.isNotEmpty()) { "Sakstatistikk krever minst én person" }
        val harKode6Gruppe = hentHarKode6Gruppe()
        val harKode6 = personer.fnr.distinct().map { fnr ->
            personOppslag.person(fnr, personer.sakstype).fold(
                { throw KunneIkkeVurdereKode6() },
                { Adressebeskyttelse.fraPdl(it.adressebeskyttelse).erKode6() },
            )
        }.any { it }
        if (harKode6 && !harKode6Gruppe) {
            sikkerLogg.error("Sakstatistikk har kode 6, men brukeren mangler forventet gruppe for strengt fortrolig adresse")
        }
        return Kode6Vurdering(harKode6)
    }

    fun vurderForBruker(behandling: Stønadsbehandling): Kode6Vurdering {
        val epsFraSøknad = (behandling as? Søknadsbehandling)
            ?.søknad?.søknadInnhold?.boforhold?.ektefellePartnerSamboer?.fnr
        return vurderForBruker(
            PersonerOgSakstype(
                behandling.sakstype,
                listOfNotNull(behandling.fnr, epsFraSøknad) + behandling.grunnlagsdata.bosituasjon.mapNotNull { it.eps },
            ),
        )
    }

    fun vurderForBruker(klage: Klage, sak: Sak? = null): Kode6Vurdering {
        val klagesak = sak ?: requireNotNull(sakRepo.hentSak(klage.sakId)) { "Fant ikke sak for klage" }
        require(klage.sakId == klagesak.id)
        val vedtakId = klage.vilkårsvurderinger?.vedtakId
        return if (vedtakId != null) {
            val behandling = klagesak.vedtakListe.filterIsInstance<Stønadsvedtak>().single { it.id == vedtakId }.behandling
            vurderForBruker(behandling)
        } else {
            val relatertBehandlingId = sakStatistikkRepo.hentInitiellBehandlingsstatistikk(klage.id, null)?.relatertBehandlingId
            check(klage.infotrygdSakId != null || relatertBehandlingId != null) {
                "Mangler relatert behandling for kode 6-vurdering av klage"
            }
            vurderForRelatertBehandling(
                klagesak,
                relatertBehandlingId,
            )
        }
    }

    fun vurderForBruker(søknad: SøknadInnhold): Kode6Vurdering =
        vurderForBruker(
            PersonerOgSakstype(
                søknad.type(),
                listOfNotNull(søknad.personopplysninger.fnr, søknad.boforhold.ektefellePartnerSamboer?.fnr),
            ),
        )

    fun vurderForBruker(sak: Sak, vedtaksdata: GjeldendeVedtaksdata): Kode6Vurdering =
        vurderForBruker(
            PersonerOgSakstype(
                sak.type,
                listOf(sak.fnr) + vedtaksdata.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.mapNotNull { it.eps },
            ),
        )

    fun vurderForRelatertBehandling(
        sak: Sak,
        relatertBehandlingId: UUID?,
    ): Kode6Vurdering {
        if (relatertBehandlingId == null) {
            return vurderForBruker(PersonerOgSakstype(sak.type, listOf(sak.fnr)))
        }
        val behandling: Stønadsbehandling = sak.søknadsbehandlinger.find { it.id.value == relatertBehandlingId }
            ?: sak.revurderinger.find { it.id.value == relatertBehandlingId }
            ?: sak.reguleringer.find { it.id.value == relatertBehandlingId }
            ?: throw IllegalStateException("Fant ikke relatert behandling for sakstatistikk")
        return vurderForBruker(behandling)
    }

    fun lagreForBruker(
        hendelse: StatistikkEvent.Behandling,
        vurdering: Kode6Vurdering,
        sessionContext: SessionContext?,
    ) = lagre(hendelse, vurdering.harKode6, sessionContext)

    fun lagreAutomatisk(
        hendelse: StatistikkEvent.Behandling,
        sessionContext: SessionContext?,
    ) = lagre(hendelse, harKode6 = false, sessionContext = sessionContext)

    private fun lagre(
        hendelse: StatistikkEvent.Behandling,
        harKode6: Boolean,
        sessionContext: SessionContext?,
    ) {
        val behandling: Behandling = when (hendelse) {
            is StatistikkEvent.Behandling.Søknad -> hendelse.søknadsbehandling
            is StatistikkEvent.Behandling.Revurdering -> hendelse.revurdering
            is StatistikkEvent.Behandling.Klage -> hendelse.klage
            is StatistikkEvent.Behandling.Stans -> hendelse.revurdering
            is StatistikkEvent.Behandling.Gjenoppta -> hendelse.revurdering
            is StatistikkEvent.Behandling.Regulering -> hendelse.regulering
            is StatistikkEvent.Behandling.ReguleringOmregning -> hendelse.regulering
        }
        val førsteLinje = sakStatistikkRepo.hentInitiellBehandlingsstatistikk(behandling.id, sessionContext)
        val statistikk = hendelse.toBehandlingsstatistikkOverordnet(clock, førsteLinje).let {
            if (harKode6) {
                it.copy(
                    opprettetAv = SKJERMET,
                    ansvarligBeslutter = it.ansvarligBeslutter?.let { SKJERMET },
                    ansvarligEnhet = VIKAFOSSEN_ENHET,
                )
            } else {
                it
            }
        }
        sakStatistikkRepo.lagreSakStatistikk(statistikk, sessionContext)
    }

    private companion object {
        const val SKJERMET = "SKJERMET"
        const val VIKAFOSSEN_ENHET = "2103"
    }

    class KunneIkkeVurdereKode6 : IllegalStateException("Kunne ikke vurdere kode 6 for sakstatistikk")
}
