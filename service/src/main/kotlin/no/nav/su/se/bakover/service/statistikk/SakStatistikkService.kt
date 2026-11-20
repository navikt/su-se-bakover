package no.nav.su.se.bakover.service.statistikk

import behandling.domain.Behandling
import behandling.domain.Stønadsbehandling
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.hentVedtakForIdEllerKast
import no.nav.su.se.bakover.domain.statistikk.Kode6Vurdering
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import person.domain.Adressebeskyttelse
import person.domain.PersonOppslag
import person.domain.PersonerOgSakstype
import tilbakekreving.domain.Tilbakekrevingsbehandling
import toBehandlingsstatistikkOverordnet
import java.time.Clock
import java.util.UUID

class SakStatistikkService(
    private val sakStatistikkRepo: SakStatistikkRepo,
    private val clock: Clock,
    private val personOppslag: PersonOppslag,
    private val sakRepo: SakRepo,
) {
    private fun vurderKode6ForPersoner(
        personer: PersonerOgSakstype,
        medSystembruker: Boolean = false,
    ): Kode6Vurdering {
        require(personer.fnr.isNotEmpty()) { "Sakstatistikk krever minst én person" }
        val harKode6 = personer.fnr.distinct().map { fnr ->
            val person = if (medSystembruker) {
                personOppslag.personMedSystembruker(fnr, personer.sakstype)
            } else {
                personOppslag.person(fnr, personer.sakstype)
            }
            person.fold(
                { throw KunneIkkeVurdereKode6() },
                { Adressebeskyttelse.fraPdl(it.adressebeskyttelse).erKode6() },
            )
        }.any { it }
        return Kode6Vurdering(harKode6)
    }

    fun vurderKode6ForStønadsbehandling(behandling: Stønadsbehandling): Kode6Vurdering =
        vurderKode6ForStønadsbehandling(behandling, medSystembruker = false)

    fun vurderKode6ForStønadsbehandlingMedSystembruker(behandling: Stønadsbehandling): Kode6Vurdering =
        vurderKode6ForStønadsbehandling(behandling, medSystembruker = true)

    private fun vurderKode6ForStønadsbehandling(
        behandling: Stønadsbehandling,
        medSystembruker: Boolean,
    ): Kode6Vurdering {
        val epsFraSøknad = (behandling as? Søknadsbehandling)
            ?.søknad?.søknadInnhold?.boforhold?.ektefellePartnerSamboer?.fnr
        return vurderKode6ForPersoner(
            PersonerOgSakstype(
                behandling.sakstype,
                listOfNotNull(behandling.fnr, epsFraSøknad) + behandling.grunnlagsdata.bosituasjon.mapNotNull { it.eps },
            ),
            medSystembruker = medSystembruker,
        )
    }

    fun vurderKode6ForKlage(klage: Klage, sak: Sak? = null): Kode6Vurdering {
        val klagesak = sak ?: requireNotNull(sakRepo.hentSak(klage.sakId)) { "Fant ikke sak for klage" }
        return vurderKode6ForBehandling(klage, klagesak, emptySet())
    }

    fun vurderKode6ForSøknad(søknad: SøknadInnhold): Kode6Vurdering =
        vurderKode6ForPersoner(
            PersonerOgSakstype(
                søknad.type(),
                listOfNotNull(søknad.personopplysninger.fnr, søknad.boforhold.ektefellePartnerSamboer?.fnr),
            ),
        )

    fun vurderKode6ForVedtaksdata(sak: Sak, vedtaksdata: GjeldendeVedtaksdata): Kode6Vurdering =
        vurderKode6ForVedtaksdata(sak, vedtaksdata, medSystembruker = false)

    fun vurderKode6ForVedtaksdataMedSystembruker(sak: Sak, vedtaksdata: GjeldendeVedtaksdata): Kode6Vurdering =
        vurderKode6ForVedtaksdata(sak, vedtaksdata, medSystembruker = true)

    private fun vurderKode6ForVedtaksdata(
        sak: Sak,
        vedtaksdata: GjeldendeVedtaksdata,
        medSystembruker: Boolean,
    ): Kode6Vurdering =
        vurderKode6ForPersoner(
            PersonerOgSakstype(
                sak.type,
                listOf(sak.fnr) + vedtaksdata.grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.mapNotNull { it.eps },
            ),
            medSystembruker = medSystembruker,
        )

    fun vurderKode6ForRelatertBehandling(
        sak: Sak,
        relatertBehandlingId: UUID?,
    ): Kode6Vurdering {
        if (relatertBehandlingId == null) {
            return vurderKode6ForPersoner(PersonerOgSakstype(sak.type, listOf(sak.fnr)))
        }
        return vurderKode6ForBehandling(finnRelatertBehandling(sak, relatertBehandlingId), sak, emptySet())
    }

    private fun finnRelatertBehandling(sak: Sak, relatertBehandlingId: UUID): Behandling =
        sak.søknadsbehandlinger.find { it.id.value == relatertBehandlingId }
            ?: sak.revurderinger.find { it.id.value == relatertBehandlingId }
            ?: sak.reguleringer.find { it.id.value == relatertBehandlingId }
            ?: sak.klager.find { it.id.value == relatertBehandlingId }
            ?: sak.behandlinger.tilbakekrevinger.find { it.id.value == relatertBehandlingId }
            ?: throw IllegalStateException("Fant ikke relatert behandling for sakstatistikk")

    private fun vurderKode6ForBehandling(
        behandling: Behandling,
        sak: Sak,
        besøkteKlager: Set<KlageId>,
    ): Kode6Vurdering {
        require(behandling.sakId == sak.id)
        return when (behandling) {
            is Stønadsbehandling -> vurderKode6ForStønadsbehandling(behandling)
            is Tilbakekrevingsbehandling -> vurderKode6ForTilbakekrevingsklage(behandling, sak)
            is Klage -> {
                check(behandling.id !in besøkteKlager) { "Sirkulær klagereferanse ved kode 6-vurdering" }
                val vedtakId = behandling.vilkårsvurderinger?.vedtakId
                val relatertBehandling = if (vedtakId != null) {
                    sak.hentVedtakForIdEllerKast(vedtakId).behandling
                } else {
                    val relatertBehandlingId = sakStatistikkRepo
                        .hentInitiellBehandlingsstatistikk(behandling.id, null)?.relatertBehandlingId
                    check(behandling.infotrygdSakId != null || relatertBehandlingId != null) {
                        "Mangler relatert behandling for kode 6-vurdering av klage"
                    }
                    relatertBehandlingId?.let { finnRelatertBehandling(sak, it) }
                }
                if (relatertBehandling == null) {
                    vurderKode6ForPersoner(PersonerOgSakstype(sak.type, listOf(sak.fnr)))
                } else {
                    vurderKode6ForBehandling(relatertBehandling, sak, besøkteKlager + behandling.id)
                }
            }
            else -> throw IllegalStateException("Behandlingstypen støttes ikke for kode 6-vurdering av klage")
        }
    }

    private fun vurderKode6ForTilbakekrevingsklage(
        behandling: Tilbakekrevingsbehandling,
        sak: Sak,
    ): Kode6Vurdering {
        val kravgrunnlag = requireNotNull(behandling.kravgrunnlag) { "Mangler kravgrunnlag for kode 6-vurdering av klage" }
        check(kravgrunnlag.grunnlagsperioder.isNotEmpty()) { "Mangler kravgrunnlagsperioder for kode 6-vurdering av klage" }
        val bosituasjoner = requireNotNull(sak.vedtakstidslinje()) { "Mangler stønadsvedtak for kode 6-vurdering av klage" }
            .flatMap { it.grunnlagsdata.bosituasjon }
        check(
            kravgrunnlag.grunnlagsperioder.all { grunnlagsperiode ->
                bosituasjoner.any { it.periode.overlapper(grunnlagsperiode.periode) }
            },
        ) { "Mangler bosituasjon i kravgrunnlagsperioden for kode 6-vurdering av klage" }
        val eps = bosituasjoner.filter { bosituasjon ->
            kravgrunnlag.grunnlagsperioder.any { it.periode.overlapper(bosituasjon.periode) }
        }.mapNotNull { it.eps }
        return vurderKode6ForPersoner(PersonerOgSakstype(sak.type, listOf(sak.fnr) + eps))
    }

    fun lagreForBrukerflyt(
        hendelse: StatistikkEvent.Behandling,
        vurdering: Kode6Vurdering,
        sessionContext: SessionContext?,
    ) = lagre(hendelse, vurdering.harKode6, sessionContext)

    fun lagreAutomatiskSystemflyt(
        hendelse: StatistikkEvent.Behandling,
        vurdering: Kode6Vurdering,
        sessionContext: SessionContext?,
    ) = lagre(hendelse, harKode6 = vurdering.harKode6, sessionContext = sessionContext)

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
