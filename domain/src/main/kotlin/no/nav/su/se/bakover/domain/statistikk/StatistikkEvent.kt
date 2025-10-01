package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Omgjøringssøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.Opphørsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import vedtak.domain.VedtakSomKanRevurderes
import java.util.UUID
import no.nav.su.se.bakover.domain.søknad.Søknad as DomeneSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling as DomeneSøknadsbehandling

// TODO jah: Statistikk er ikke per say domenet vårt, vi burde bare publisert generelle hendelser til en Hendelsestype som igjen statistikk kunne lyttet på (gjerne vha. coroutines).
sealed interface StatistikkEvent {

    data class SakOpprettet(val sak: Sak) : StatistikkEvent

    /**
     * Søknadshendelse før det er startet en søknadsbehandling
     */
    sealed interface Søknad : StatistikkEvent {
        val søknad: DomeneSøknad
        val saksnummer: Saksnummer

        data class Mottatt(
            override val søknad: DomeneSøknad.Ny,
            override val saksnummer: Saksnummer,
        ) : Søknad

        data class Lukket(
            override val søknad: DomeneSøknad.Journalført.MedOppgave.Lukket,
            override val saksnummer: Saksnummer,
        ) : Søknad
    }

    sealed interface Behandling : StatistikkEvent {

        sealed interface Søknad : Behandling {
            val søknadsbehandling: DomeneSøknadsbehandling

            data class Opprettet(
                override val søknadsbehandling: VilkårsvurdertSøknadsbehandling.Uavklart,
                // TODO jah: Erstatt med saksbehandler fra behandlinga hvis det blir implmentert.
                val saksbehandler: NavIdentBruker.Saksbehandler,
            ) : Søknad

            // Skjer kun hvis det finnes en avslått søknad
            data class OpprettetOmgjøring(
                override val søknadsbehandling: Omgjøringssøknadsbehandling,
                val saksbehandler: NavIdentBruker.Saksbehandler,
                val relatertId: UUID,
            ) : Søknad

            sealed interface TilAttestering : Søknad {
                data class Innvilget(
                    override val søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget,
                ) : TilAttestering

                data class Avslag(
                    override val søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag,
                ) : TilAttestering
            }

            sealed interface Underkjent : Søknad {
                data class Innvilget(
                    override val søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget,
                ) : Underkjent

                data class Avslag(
                    override val søknadsbehandling: UnderkjentSøknadsbehandling.Avslag,
                ) : Underkjent
            }

            sealed interface Iverksatt : Søknad {

                val vedtak: vedtak.domain.Stønadsvedtak

                data class Innvilget(
                    override val vedtak: VedtakInnvilgetSøknadsbehandling,
                ) : Iverksatt {
                    override val søknadsbehandling: IverksattSøknadsbehandling.Innvilget
                        get() = vedtak.behandling
                }

                data class Avslag(
                    override val vedtak: Avslagsvedtak,
                ) : Iverksatt {
                    override val søknadsbehandling: IverksattSøknadsbehandling.Avslag
                        get() = vedtak.behandling
                }
            }

            data class Lukket(
                override val søknadsbehandling: LukketSøknadsbehandling,
                // TODO jah: Erstatt med saksbehandler fra behandlinga hvis det blir implmentert.
                val lukketAv: NavIdentBruker.Saksbehandler,
            ) : Søknad
        }

        sealed interface Revurdering : Behandling {
            val revurdering: no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering

            data class Opprettet(
                override val revurdering: OpprettetRevurdering,
                val relatertId: UUID? = null,
            ) : Revurdering

            sealed interface TilAttestering : Revurdering {
                data class Innvilget(
                    override val revurdering: no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering.Innvilget,
                ) : TilAttestering

                data class Opphør(
                    override val revurdering: no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering.Opphørt,
                ) : TilAttestering
            }

            sealed interface Underkjent : Revurdering {
                data class Innvilget(override val revurdering: UnderkjentRevurdering.Innvilget) : Underkjent
                data class Opphør(override val revurdering: UnderkjentRevurdering.Opphørt) : Underkjent
            }

            sealed interface Iverksatt : Revurdering {
                val vedtak: VedtakSomKanRevurderes

                data class Innvilget(
                    override val vedtak: VedtakInnvilgetRevurdering,
                ) : Iverksatt {
                    override val revurdering: IverksattRevurdering.Innvilget
                        get() = vedtak.behandling
                }

                data class Opphørt(
                    override val vedtak: Opphørsvedtak,
                ) : Iverksatt {
                    override val revurdering: IverksattRevurdering.Opphørt
                        get() = vedtak.behandling
                }
            }

            data class Avsluttet(
                override val revurdering: AvsluttetRevurdering,
                // TODO jah: Erstatt med saksbehandler fra behandlinga hvis det blir implmentert.
                val saksbehandler: NavIdentBruker.Saksbehandler,
            ) : Revurdering
        }

        sealed interface Stans : Behandling {
            val revurdering: StansAvYtelseRevurdering

            data class Opprettet(
                override val revurdering: StansAvYtelseRevurdering.SimulertStansAvYtelse,
            ) : Stans

            data class Iverksatt(
                val vedtak: VedtakStansAvYtelse,
            ) : Stans {
                override val revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse
                    get() = vedtak.behandling
            }

            data class Avsluttet(
                override val revurdering: StansAvYtelseRevurdering.AvsluttetStansAvYtelse,
            ) : Stans
        }

        sealed interface Gjenoppta : Behandling {
            val revurdering: GjenopptaYtelseRevurdering

            data class Opprettet(
                override val revurdering: GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse,
            ) : Gjenoppta

            data class Iverksatt(
                val vedtak: VedtakGjenopptakAvYtelse,
            ) : Gjenoppta {
                override val revurdering: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse
                    get() = vedtak.behandling
            }

            data class Avsluttet(
                override val revurdering: GjenopptaYtelseRevurdering.AvsluttetGjenoppta,
            ) : Gjenoppta
        }

        sealed interface Klage : Behandling {
            val klage: no.nav.su.se.bakover.domain.klage.Klage

            data class Opprettet(override val klage: OpprettetKlage) : Klage
            data class Oversendt(override val klage: OversendtKlage) : Klage
            data class Avvist(
                val vedtak: Klagevedtak.Avvist,
            ) : Klage {
                override val klage: IverksattAvvistKlage
                    get() = vedtak.behandling
            }

            data class Avsluttet(override val klage: AvsluttetKlage) : Klage
        }
    }

    /**
     * Brukes til stønadsstatistikk
     */
    data class Stønadsvedtak(val vedtak: VedtakSomKanRevurderes, val hentSak: () -> Sak) : StatistikkEvent
}
