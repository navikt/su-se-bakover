package no.nav.su.se.bakover.service.statistikk.mappers

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
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
            behandlingStatus = BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(søknadsbehandling).status,
            behandlingStatusBeskrivelse = BehandlingStatusOgBehandlingStatusBeskrivelseMapper.map(søknadsbehandling).beskrivelse,
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
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling),
                    )
                }

                is Søknadsbehandling.TilAttestering -> {
                    copy(
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling),
                    )
                }

                is Søknadsbehandling.Underkjent -> {
                    copy(
                        saksbehandler = søknadsbehandling.saksbehandler.navIdent,
                        beslutter = søknadsbehandling.attesteringer.hentSisteAttestering().attestant.navIdent,
                        behandlingYtelseDetaljer = behandlingYtelseDetaljer(søknadsbehandling),
                    )
                }

                is LukketSøknadsbehandling -> {
                    copy(
                        resultat = ResultatOgBegrunnelseMapper.map(søknadsbehandling).resultat,
                        avsluttet = true,
                        behandlingStatus = "LUKKET",
                        behandlingStatusBeskrivelse = "Søknadsbehandling lukket",
                    )
                }

                else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
            }
        }
    }

    fun map(
        søknad: Søknad,
        saksnummer: Saksnummer,
        søknadStatus: Statistikk.Behandling.SøknadStatus,
    ): Statistikk.Behandling =
        Statistikk.Behandling(
            funksjonellTid = when (søknad) {
                is Søknad.Journalført.MedOppgave.Lukket -> søknad.lukketTidspunkt
                else -> søknad.opprettet
            },
            tekniskTid = when (søknad) {
                is Søknad.Journalført.MedOppgave.Lukket -> søknad.lukketTidspunkt
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
                is Søknad.Journalført.MedOppgave.Lukket -> when (søknad) {
                    is Søknad.Journalført.MedOppgave.Lukket.Bortfalt -> "BORTFALT"
                    is Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker -> "TRUKKET"
                    is Søknad.Journalført.MedOppgave.Lukket.Avvist -> "AVVIST"
                }
                else -> null
            },
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = when (søknad) {
                is Søknad.Journalført.MedOppgave.Lukket -> søknad.lukketAv.toString()
                else -> null
            },
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = when (søknad) {
                is Søknad.Journalført.MedOppgave.Lukket -> true
                else -> false
            },
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
            relatertBehandlingId = revurdering.tilRevurdering,
            avsluttet = false,
        ).apply {
            return when (revurdering) {
                is OpprettetRevurdering -> this
                is RevurderingTilAttestering -> this
                is IverksattRevurdering -> {
                    val resultatOgBegrunnelse = RevurderingResultatOgBegrunnelseMapper.map(revurdering, clock)

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
                        beslutter = revurdering.attestering.attestant.navIdent,
                    )
                }

                is AvsluttetRevurdering -> {
                    val resultatOgBegrunnelse = RevurderingResultatOgBegrunnelseMapper.map(revurdering, clock)
                    copy(
                        avsluttet = true,
                        resultat = resultatOgBegrunnelse.resultat,
                        resultatBegrunnelse = resultatOgBegrunnelse.begrunnelse,
                    )
                }

                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
        }
    }

    fun map(gjenopptak: GjenopptaYtelseRevurdering): Statistikk.Behandling {
        Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = gjenopptak.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = gjenopptak.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = gjenopptak.id,
            sakId = gjenopptak.sakId,
            saksnummer = gjenopptak.saksnummer.nummer,
            behandlingStatus = BehandlingStatusMapper.map(gjenopptak),
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(gjenopptak),
            versjon = clock.millis(),
            relatertBehandlingId = gjenopptak.tilRevurdering,
            totrinnsbehandling = false,
            avsluttet = false,
        ).apply {
            return when (gjenopptak) {
                is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
                    copy(
                        beslutter = gjenopptak.attesteringer.hentSisteAttestering().attestant.navIdent,
                        resultat = RevurderingResultatOgBegrunnelseMapper.map(gjenopptak).resultat,
                        resultatBegrunnelse = RevurderingResultatOgBegrunnelseMapper.map(gjenopptak).begrunnelse,
                    )
                }

                is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> this
                is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> {
                    copy(
                        avsluttet = true,
                        resultat = RevurderingResultatOgBegrunnelseMapper.lukket(),
                    )
                }
            }
        }
    }

    fun map(stans: StansAvYtelseRevurdering): Statistikk.Behandling {
        Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = stans.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = stans.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = stans.id,
            sakId = stans.sakId,
            saksnummer = stans.saksnummer.nummer,
            behandlingStatus = BehandlingStatusMapper.map(stans),
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(stans),
            versjon = clock.millis(),
            relatertBehandlingId = stans.tilRevurdering,
            totrinnsbehandling = false,
            avsluttet = false,
        ).apply {
            return when (stans) {
                is StansAvYtelseRevurdering.IverksattStansAvYtelse -> {
                    copy(
                        beslutter = stans.attesteringer.hentSisteAttestering().attestant.navIdent,
                        resultat = RevurderingResultatOgBegrunnelseMapper.map(stans).resultat,
                        resultatBegrunnelse = RevurderingResultatOgBegrunnelseMapper.map(stans).begrunnelse,
                    )
                }

                is StansAvYtelseRevurdering.SimulertStansAvYtelse -> this
                is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> {
                    copy(
                        avsluttet = true,
                        resultat = RevurderingResultatOgBegrunnelseMapper.lukket(),
                    )
                }
            }
        }
    }

    fun map(klage: Klage): Statistikk.Behandling {
        val nå = Tidspunkt.now(clock)
        return Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.KLAGE,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.KLAGE.beskrivelse,
            funksjonellTid = nå,
            tekniskTid = nå,
            registrertDato = klage.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = klage.datoKlageMottatt,
            behandlingId = klage.id,
            sakId = klage.sakId,
            saksnummer = klage.saksnummer.nummer,
            behandlingStatus = BehandlingStatusMapper.map(klage),
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(klage),
            versjon = clock.millis(),
            saksbehandler = klage.saksbehandler.navIdent,
            relatertBehandlingId = klage.vilkårsvurderinger?.vedtakId,
            avsluttet = when (klage) {
                // avsluttet = false er et spesialønske fra statistikk når det kommer til OversendtKlage.
                // De har et bredere perspektiv og vil ikke se denne klagen som avsluttet.
                // Førsteinstansen vil anse den som ferdigbehandlet inntil den potensielt blir returnert av forskjellige grunner.
                is OversendtKlage -> false
                else -> !klage.erÅpen()
            },
            totrinnsbehandling = klage.attesteringer != null,
            beslutter = klage.attesteringer?.hentSisteAttestering()?.attestant?.navIdent,
            // Det er ønskelig å sende med resultat og resultatBegrunnelse i de tilfellene en klagebehandling er ferdigstilt (endelige tilstander) sett fra Klageinstans/Statistikk sin side.
            // resultatBegrunnelse: Ønsker en årsak (predefinerte verdier) eller null dersom vi ikke har det (ønsker ikke fritekstfelter). Ønsker ikke statiske tekster som forklarer resultatet.
            resultat = when (klage) {
                is AvsluttetKlage -> "Avsluttet"
                is IverksattAvvistKlage -> "Avvist"
                is OversendtKlage -> "Opprettholdt"
                else -> null
            },
            resultatBegrunnelse = when (klage) {
                // Vi har ikke noen prederfinerte verdier for begrunnelse/årsak for avsluttet klage så denne er null inntil videre.
                is IverksattAvvistKlage -> klage.vilkårsvurderinger.mapToResultatBegrunnelse()
                is OversendtKlage -> (klage.vurderinger.vedtaksvurdering as VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold).hjemler.mapToResultatBegrunnelse()
                else -> null
            },
        )
    }

    private fun behandlingYtelseDetaljer(
        behandling: Behandling,
    ): List<Statistikk.BehandlingYtelseDetaljer> {
        return behandling.grunnlagsdata.bosituasjon.filterIsInstance<Grunnlag.Bosituasjon.Fullstendig>().map {
            Statistikk.BehandlingYtelseDetaljer(satsgrunn = it.stønadsklassifisering())
        }
    }

    internal object BehandlingStatusOgBehandlingStatusBeskrivelseMapper {
        data class BehandlingStatusOgBehandlingStatusBeskrivelse(
            val status: String,
            val beskrivelse: String,
        )

        fun map(søknadsbehandling: Søknadsbehandling): BehandlingStatusOgBehandlingStatusBeskrivelse =
            when (søknadsbehandling) {
                is Søknadsbehandling.Vilkårsvurdert.Uavklart -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "OPPRETTET",
                    "Ny søknadsbehandling opprettet",
                )

                is Søknadsbehandling.TilAttestering.Innvilget -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "TIL_ATTESTERING_INNVILGET",
                    "Innvilget søkndsbehandling sendt til attestering",
                )

                is Søknadsbehandling.TilAttestering.Avslag -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "TIL_ATTESTERING_AVSLAG",
                    "Avslått søknadsbehanding sendt til attestering",
                )

                is Søknadsbehandling.Underkjent.Innvilget -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "UNDERKJENT_INNVILGET",
                    "Innvilget søknadsbehandling sendt tilbake fra attestant til saksbehandler",
                )

                is Søknadsbehandling.Underkjent.Avslag -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "UNDERKJENT_AVSLAG",
                    "Avslått søknadsbehandling sendt tilbake fra attestant til saksbehandler",
                )

                is Søknadsbehandling.Iverksatt.Innvilget -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "IVERKSATT_INNVILGET",
                    "Innvilget søknadsbehandling iverksatt",
                )

                is Søknadsbehandling.Iverksatt.Avslag -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "IVERKSATT_AVSLAG",
                    "Avslått søknadsbehandling iverksatt",
                )

                is LukketSøknadsbehandling -> BehandlingStatusOgBehandlingStatusBeskrivelse(
                    "LUKKET",
                    "Søknadsbehandling er lukket",
                )

                is Søknadsbehandling.Beregnet,
                is Søknadsbehandling.Vilkårsvurdert.Innvilget,
                is Søknadsbehandling.Vilkårsvurdert.Avslag,
                is Søknadsbehandling.Simulert,
                -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
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
                is AvsluttetRevurdering -> "AVSLUTTET"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }

        fun map(gjenopptak: GjenopptaYtelseRevurdering) = when (gjenopptak) {
            is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> "IVERKSATT_GJENOPPTAK"
            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> "SIMULERT_GJENOPPTAK"
            is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> "AVSLUTTET_GJENOPPTAK"
        }

        fun map(stans: StansAvYtelseRevurdering) = when (stans) {
            is StansAvYtelseRevurdering.IverksattStansAvYtelse -> "IVERKSATT_STANS"
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> "SIMULERT_STANS"
            is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> "AVSLUTTET_STANS"
        }

        fun map(klage: Klage) = when (klage) {
            is OpprettetKlage -> "OPPRETTET"
            is AvvistKlage -> "UNDER_BEHANDLING"
            is VilkårsvurdertKlage -> "UNDER_BEHANDLING"
            is VurdertKlage -> "UNDER_BEHANDLING"
            is KlageTilAttestering -> "TIL_ATTESTERING"
            is AvsluttetKlage -> "AVSLUTTET"
            is IverksattAvvistKlage -> "IVERKSATT"
            is OversendtKlage -> "OVERSENDT"
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
                is AvsluttetRevurdering -> "Revurdering avsluttet"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }

        fun map(gjenopptak: GjenopptaYtelseRevurdering): String =
            when (gjenopptak) {
                is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> "Ytelsen er gjenopptatt"
                is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> "Opprettet og simulert gjenopptak av ytelse"
                is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> "Gjenopptak av ytelsen er avsluttet"
            }

        fun map(stans: StansAvYtelseRevurdering) = when (stans) {
            is StansAvYtelseRevurdering.IverksattStansAvYtelse -> "Ytelsen er stanset"
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> "Opprettet og simulert stans av ytelsen"
            is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> "Stans av ytelsen er avsluttet"
        }

        fun map(klage: Klage) = when (klage) {
            is OpprettetKlage -> "Klagebehandling ble opprettet av saksbehandler"
            is AvvistKlage, is VilkårsvurdertKlage, is VurdertKlage -> "Klagen er under behandling"
            is KlageTilAttestering -> "Klagen er sendt til attestering"
            is AvsluttetKlage -> "Klagebehandling ble avsluttet"
            is IverksattAvvistKlage -> "Klagen ble avvist"
            is OversendtKlage -> "Klagen er oversendt til klageinstans"
        }
    }

    data class ResultatOgBegrunnelse(
        val resultat: String,
        val begrunnelse: String?,
    )

    internal object ResultatOgBegrunnelseMapper {
        private const val innvilget = "Innvilget"
        private const val avslått = "Avslått"
        private const val lukket = "Lukket"

        fun map(søknadsbehandling: Søknadsbehandling): ResultatOgBegrunnelse = when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Innvilget -> {
                ResultatOgBegrunnelse(innvilget, null)
            }

            is Søknadsbehandling.Iverksatt.Avslag -> {
                ResultatOgBegrunnelse(avslått, søknadsbehandling.avslagsgrunner.joinToString(","))
            }

            is LukketSøknadsbehandling -> {
                ResultatOgBegrunnelse(lukket, null)
            }

            else -> throw ManglendeStatistikkMappingException(this, søknadsbehandling::class.java)
        }
    }

    internal object RevurderingResultatOgBegrunnelseMapper {
        private const val innvilget = "Innvilget"
        private const val opphørt = "Opphørt"
        private const val lukket = "Lukket"
        private const val ingenEndring = "Uendret"
        private const val ingenEndringBegrunnelse = "Mindre enn 10% endring i inntekt"

        private const val stans = "Stanset"
        private const val gjenopptak = "Gjenopptatt"

        internal fun map(revurdering: Revurdering, clock: Clock): ResultatOgBegrunnelse = when (revurdering) {
            is IverksattRevurdering.Innvilget -> ResultatOgBegrunnelse(innvilget, null)
            is IverksattRevurdering.Opphørt -> ResultatOgBegrunnelse(
                opphørt,
                listUtOpphørsgrunner(revurdering.utledOpphørsgrunner(clock)),
            )

            is IverksattRevurdering.IngenEndring -> ResultatOgBegrunnelse(ingenEndring, ingenEndringBegrunnelse)
            is AvsluttetRevurdering -> ResultatOgBegrunnelse(lukket, null)
            else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
        }

        fun map(stansAvYtelse: StansAvYtelseRevurdering.IverksattStansAvYtelse) =
            ResultatOgBegrunnelse(stans, stansAvYtelse.revurderingsårsak.årsak.hentGyldigStansBegrunnelse())

        fun map(gjenopptakAvYtelse: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse) = ResultatOgBegrunnelse(
            gjenopptak,
            gjenopptakAvYtelse.revurderingsårsak.årsak.hentGyldigGjenopptakBegrunnelse(),
        )

        fun lukket() = lukket

        private fun listUtOpphørsgrunner(opphørsgrunner: List<Opphørsgrunn>): String = opphørsgrunner.joinToString(",")
        private fun Revurderingsårsak.Årsak.hentGyldigStansBegrunnelse() = when (this) {
            Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING -> "Manglende kontrollerklæring"
            else -> throw RuntimeException("Feil ved mapping av gyldig årsak for Stans. $this er ikke en gyldig årsak for stans av ytelse")
        }

        private fun Revurderingsårsak.Årsak.hentGyldigGjenopptakBegrunnelse() = when (this) {
            Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING -> "Mottatt kontrollerklæring"
            else -> throw RuntimeException("Feil ved mapping av gyldig årsak for Gjenopptak. $this er ikke en gyldig årsak for gjenopptak av ytelse")
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
