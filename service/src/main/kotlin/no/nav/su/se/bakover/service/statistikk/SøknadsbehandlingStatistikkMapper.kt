package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock

internal class SøknadsbehandlingStatistikkMapper(
    private val clock: Clock
) {
    fun map(søknadsbehandling: Søknadsbehandling): Statistikk.Behandling = Statistikk.Behandling(
        behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
        behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
        funksjonellTid = FunksjonellTidMapper.map(søknadsbehandling),
        tekniskTid = Tidspunkt.now(clock),
        registrertDato = RegistrertDatoMapper.map(søknadsbehandling),
        mottattDato = søknadsbehandling.opprettet.toLocalDate(zoneIdOslo),
        behandlingId = søknadsbehandling.id,
        sakId = søknadsbehandling.sakId,
        søknadId = søknadsbehandling.søknad.id,
        saksnummer = søknadsbehandling.saksnummer.nummer,
        behandlingStatus = BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(søknadsbehandling.status).status.toString(),
        behandlingStatusBeskrivelse = BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(søknadsbehandling.status).beskrivelse,
        versjon = clock.millis(),
        avsluttet = false,
    ).apply {
        return when (søknadsbehandling) {
            is Søknadsbehandling.Vilkårsvurdert.Uavklart -> {
                this
            }
            is Søknadsbehandling.Iverksatt -> {
                copy(
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    beslutter = søknadsbehandling.attestering.attestant.navIdent,
                    resultat = ResultatOgBegrunnelseMapper.map(søknadsbehandling).resultat,
                    resultatBegrunnelse = ResultatOgBegrunnelseMapper.map(søknadsbehandling).begrunnelse,
                    avsluttet = true
                )
            }
            is Søknadsbehandling.TilAttestering -> {
                copy(
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                )
            }
            is Søknadsbehandling.Underkjent -> {
                copy(
                    saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                    beslutter = søknadsbehandling.attestering.attestant.navIdent
                )
            }
            else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
        }
    }

    internal object BehandlingStatusOgBehandlingStatusBeskrivelseMapper {
        data class BehandlingStatusOgBehandlingStatusBeskrivelse(
            val status: BehandlingsStatus,
            val beskrivelse: String
        )

        fun map(status: BehandlingsStatus): BehandlingStatusOgBehandlingStatusBeskrivelse =
            when (status) {
                BehandlingsStatus.OPPRETTET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Ny søknadsbehandling opprettet"
                )
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søkndsbehandling sendt til attestering"
                )
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehanding sendt til attestering"
                )
                BehandlingsStatus.UNDERKJENT_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler"
                )
                BehandlingsStatus.UNDERKJENT_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler"
                )
                BehandlingsStatus.IVERKSATT_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søknadsbehandling iverksatt"
                )
                BehandlingsStatus.IVERKSATT_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehandling iverksatt"
                )
                else -> throw ManglendeStatistikkMappingException(this, status::class.java)
            }
    }

    internal object ResultatOgBegrunnelseMapper {
        private const val innvilget = "Innvilget"
        private const val avslått = "Avslått"

        data class ResultatOgBegrunnelse(
            val resultat: String,
            val begrunnelse: String?
        )

        fun map(søknadsbehandling: Søknadsbehandling): ResultatOgBegrunnelse = when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                ResultatOgBegrunnelse(innvilget, null)
            }
            is Søknadsbehandling.Iverksatt.Avslag -> {
                ResultatOgBegrunnelse(avslått, søknadsbehandling.avslagsgrunner.joinToString(","))
            }
            else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
        }
    }

    // TODO ai 08.03.2021: Se over dette igen, misstänker att det har blivit misstolkat
    internal object FunksjonellTidMapper {
        fun map(søknadsbehandling: Søknadsbehandling) = when (søknadsbehandling) {
            is Søknadsbehandling.Vilkårsvurdert.Uavklart -> søknadsbehandling.opprettet
            is Søknadsbehandling.Iverksatt.Avslag -> søknadsbehandling.opprettet
            is Søknadsbehandling.Iverksatt.Innvilget -> søknadsbehandling.beregning.startOfFirstDay()
            is Søknadsbehandling.TilAttestering.Avslag -> søknadsbehandling.opprettet
            is Søknadsbehandling.TilAttestering.Innvilget -> søknadsbehandling.beregning.startOfFirstDay()
            is Søknadsbehandling.Underkjent.Avslag -> søknadsbehandling.opprettet
            is Søknadsbehandling.Underkjent.Innvilget -> søknadsbehandling.beregning.startOfFirstDay()
            else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
        }

        private fun Beregning.startOfFirstDay() = getPeriode().getFraOgMed().startOfDay(zoneIdOslo)
    }

    internal object RegistrertDatoMapper {
        fun map(søknadsbehandling: Søknadsbehandling) =
            when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
                is ForNav.DigitalSøknad -> søknadsbehandling.opprettet.toLocalDate(zoneIdOslo)
                is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
            }
    }
}

data class ManglendeStatistikkMappingException(
    private val mapper: Any,
    private val clazz: Class<*>,
    val msg: String = "${mapper::class.qualifiedName} støtter ikke mapping for klasse ${clazz.name}"
) : RuntimeException(msg)
