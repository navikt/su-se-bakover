package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.statistikk.Statistikk
import no.nav.su.se.bakover.service.statistikk.stønadsklassifisering
import java.time.Clock

class BehandlingStatistikkMapper(
    private val clock: Clock,
) {
    fun map(søknadsbehandling: Søknadsbehandling): Statistikk.Behandling {
        val nå = Tidspunkt.now(clock)
        Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            funksjonellTid = nå, // Burde nok være tidspunktet når noe faktisk skjedde, typisk knyttet til en statusendring på behandlingen
            tekniskTid = nå,
            registrertDato = søknadsbehandling.søknad.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = MottattDatoMapper.map(søknadsbehandling),
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
                        beslutter = søknadsbehandling.attesteringer.hentSisteAttestering().attestant.navIdent,
                        resultat = ResultatOgBegrunnelseMapper.map(søknadsbehandling).resultat,
                        resultatBegrunnelse = ResultatOgBegrunnelseMapper.map(søknadsbehandling).begrunnelse,
                        avsluttet = true,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling)
                    )
                }
                is Søknadsbehandling.TilAttestering -> {
                    copy(
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling)
                    )
                }
                is Søknadsbehandling.Underkjent -> {
                    copy(
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        beslutter = søknadsbehandling.attesteringer.hentSisteAttestering().attestant.navIdent,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling)
                    )
                }
                else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
            }
        }
    }

    fun map(
        søknad: Søknad,
        saksnummer: Saksnummer,
        søknadStatus: Statistikk.Behandling.SøknadStatus
    ): Statistikk.Behandling =
        Statistikk.Behandling(
            funksjonellTid = when (søknad) {
                is Søknad.Lukket -> søknad.lukketTidspunkt
                else -> søknad.opprettet
            },
            tekniskTid = when (søknad) {
                is Søknad.Lukket -> søknad.lukketTidspunkt
                else -> søknad.opprettet
            },
            mottattDato = when (val forNav = søknad.søknadInnhold.forNav) {
                is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
                is ForNav.DigitalSøknad -> søknad.opprettet.toLocalDate(zoneIdOslo)
            },
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.SOKNAD.beskrivelse,
            behandlingStatus = søknadStatus.name,
            behandlingStatusBeskrivelse = søknadStatus.beskrivelse,
            totrinnsbehandling = false,
            versjon = clock.millis(),
            resultat = when (søknad) {
                is Søknad.Lukket -> søknad.lukketType.value
                else -> null
            },
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = when (søknad) {
                is Søknad.Lukket -> søknad.lukketAv.toString()
                else -> null
            },
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = when (søknad) {
                is Søknad.Lukket -> true
                else -> false
            }
        )

    fun map(revurdering: Revurdering): Statistikk.Behandling {
        val nå = Tidspunkt.now(clock)
        Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            funksjonellTid = nå,
            tekniskTid = nå,
            registrertDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = revurdering.id,
            sakId = revurdering.sakId,
            saksnummer = revurdering.saksnummer.nummer,
            behandlingStatus = BehandlingStatusMapper.map(revurdering),
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(revurdering),
            versjon = clock.millis(),
            saksbehandler = revurdering.saksbehandler.navIdent,
            relatertBehandlingId = revurdering.tilRevurdering.id,
            avsluttet = false
        ).apply {
            return when (revurdering) {
                is OpprettetRevurdering -> this
                is RevurderingTilAttestering -> this
                is IverksattRevurdering -> {
                    val resultatOgBegrunnelse = RevurderingResultatOgBegrunnelseMapper.map(revurdering)

                    copy(
                        resultat = resultatOgBegrunnelse.resultat,
                        resultatBegrunnelse = resultatOgBegrunnelse.begrunnelse,
                        beslutter = revurdering.attestering.attestant.navIdent,
                        avsluttet = true,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(revurdering),
                    )
                }
                is UnderkjentRevurdering -> {
                    copy(
                        beslutter = revurdering.attestering.attestant.navIdent
                    )
                }
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
        }
    }

    private fun behandlingYtelseDetaljer(
        behandling: Behandling
    ): List<Statistikk.BehandlingYtelseDetaljer> {
        return behandling.grunnlagsdata.bosituasjon.map {
            Statistikk.BehandlingYtelseDetaljer(satsgrunn = it.stønadsklassifisering())
        }
    }

    internal object BehandlingStatusOgBehandlingStatusBeskrivelseMapper {
        data class BehandlingStatusOgBehandlingStatusBeskrivelse(
            val status: BehandlingsStatus,
            val beskrivelse: String,
        )

        fun map(status: BehandlingsStatus): BehandlingStatusOgBehandlingStatusBeskrivelse =
            when (status) {
                BehandlingsStatus.OPPRETTET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Ny søknadsbehandling opprettet",
                )
                BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søkndsbehandling sendt til attestering",
                )
                BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehanding sendt til attestering",
                )
                BehandlingsStatus.UNDERKJENT_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
                )
                BehandlingsStatus.UNDERKJENT_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler",
                )
                BehandlingsStatus.IVERKSATT_INNVILGET -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Innvilget søknadsbehandling iverksatt",
                )
                BehandlingsStatus.IVERKSATT_AVSLAG -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    status,
                    "Avslått søknadsbehandling iverksatt",
                )
                else -> throw ManglendeStatistikkMappingException(this, status::class.java)
            }
    }

    internal object BehandlingStatusMapper {
        fun map(revurdering: Revurdering): String =
            when (revurdering) {
                is OpprettetRevurdering -> "OPPRETTET"
                is IverksattRevurdering.Innvilget -> "IVERKSATT_INNVILGET"
                is IverksattRevurdering.Opphørt -> "IVERKSATT_OPPHØRT"
                is IverksattRevurdering.IngenEndring -> "IVERKSATT_INGEN_ENDRING"
                is RevurderingTilAttestering.Innvilget -> "TIL_ATTESTERING_INNVILGET"
                is RevurderingTilAttestering.Opphørt -> "TIL_ATTESTERING_OPPHØRT"
                is RevurderingTilAttestering.IngenEndring -> "TIL_ATTESTERING_INGEN_ENDRING"
                is UnderkjentRevurdering.Innvilget -> "UNDERKJENT_INNVILGET"
                is UnderkjentRevurdering.Opphørt -> "UNDERKJENT_OPPHØRT"
                is UnderkjentRevurdering.IngenEndring -> "UNDERKJENT_INGEN_ENDRING"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
    }

    internal object BehandlingStatusBeskrivelseMapper {
        fun map(revurdering: Revurdering): String =
            when (revurdering) {
                is OpprettetRevurdering -> "Ny revurdering opprettet"
                is RevurderingTilAttestering.Innvilget -> "Innvilget revurdering sendt til attestering"
                is RevurderingTilAttestering.Opphørt -> "Opphørt revurdering sendt til attestering"
                is RevurderingTilAttestering.IngenEndring -> "Revurdering uten endring i ytelse sendt til attestering"
                is IverksattRevurdering.Innvilget -> "Innvilget revurdering iverksatt"
                is IverksattRevurdering.Opphørt -> "Opphørt revurdering iverksatt"
                is IverksattRevurdering.IngenEndring -> "Revurdering uten endring i ytelse iverksatt"
                is UnderkjentRevurdering.Innvilget -> "Innvilget revurdering underkjent"
                is UnderkjentRevurdering.Opphørt -> "Opphørt revurdering underkjent"
                is UnderkjentRevurdering.IngenEndring -> "Revurdering uten endring i ytelse underkjent"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
    }

    data class ResultatOgBegrunnelse(
        val resultat: String,
        val begrunnelse: String?,
    )

    internal object ResultatOgBegrunnelseMapper {
        private const val innvilget = "Innvilget"
        private const val avslått = "Avslått"

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

    internal object RevurderingResultatOgBegrunnelseMapper {
        private const val innvilget = "Innvilget"
        private const val opphørt = "Opphørt"
        private const val ingenEndring = "Uendret"
        private const val ingenEndringBegrunnelse = "Mindre enn 10% endring i inntekt"

        private const val stans = "Stans"
        private const val gjenopptak = "Gjenopptak"

        internal fun map(revurdering: Revurdering): ResultatOgBegrunnelse = when (revurdering) {
            is IverksattRevurdering.Innvilget -> ResultatOgBegrunnelse(innvilget, null)
            is IverksattRevurdering.Opphørt -> ResultatOgBegrunnelse(opphørt, listUtOpphørsgrunner(revurdering.utledOpphørsgrunner()))
            is IverksattRevurdering.IngenEndring -> ResultatOgBegrunnelse(ingenEndring, ingenEndringBegrunnelse)
            else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
        }

        fun map(stansAvYtelse: StansAvYtelseRevurdering.IverksattStansAvYtelse) = ResultatOgBegrunnelse(stans, stansAvYtelse.revurderingsårsak.årsak.hentGyldigStansBegrunnelse())
        fun map(gjenopptakAvYtelse: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse) = ResultatOgBegrunnelse(
            gjenopptak, gjenopptakAvYtelse.revurderingsårsak.årsak.hentGyldigGjenopptakBegrunnelse()
        )

        private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String = opphørsgrunner.joinToString(",")
        private fun Revurderingsårsak.Årsak.hentGyldigStansBegrunnelse() = when (this) {
            Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING -> "Manglende kontrollerklæring"
            else -> throw RuntimeException("$this er ikke en gyldig årsak for stans av ytelse")
        }
        private fun Revurderingsårsak.Årsak.hentGyldigGjenopptakBegrunnelse() = when (this) {
            Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING -> "Mottatt kontrollerklæring"
            else -> throw RuntimeException("$this er ikke en gyldig årsak for gjenopptak av ytelse")
        }
    }

    internal object MottattDatoMapper {
        fun map(søknadsbehandling: Søknadsbehandling) =
            when (val forNav = søknadsbehandling.søknad.søknadInnhold.forNav) {
                is ForNav.DigitalSøknad -> søknadsbehandling.søknad.opprettet.toLocalDate(zoneIdOslo)
                is ForNav.Papirsøknad -> forNav.mottaksdatoForSøknad
            }
    }
}

data class ManglendeStatistikkMappingException(
    private val mapper: Any,
    private val clazz: Class<*>,
    val msg: String = "${mapper::class.qualifiedName} støtter ikke mapping for klasse ${clazz.name}",
) : RuntimeException(msg)
