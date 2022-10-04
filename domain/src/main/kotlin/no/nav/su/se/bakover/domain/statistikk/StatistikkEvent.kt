package no.nav.su.se.bakover.domain.statistikk

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
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
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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
                override val søknadsbehandling: DomeneSøknadsbehandling.Vilkårsvurdert.Uavklart,
                // TODO jah: Erstatt med saksbehandler fra behandlinga hvis det blir implmentert.
                val saksbehandler: NavIdentBruker.Saksbehandler,
            ) : Søknad

            sealed interface TilAttestering : Søknad {
                data class Innvilget(
                    override val søknadsbehandling: DomeneSøknadsbehandling.TilAttestering.Innvilget,
                ) : TilAttestering

                data class Avslag(
                    override val søknadsbehandling: DomeneSøknadsbehandling.TilAttestering.Avslag,
                ) : TilAttestering
            }

            sealed interface Underkjent : Søknad {
                data class Innvilget(
                    override val søknadsbehandling: DomeneSøknadsbehandling.Underkjent.Innvilget,
                ) : Underkjent

                data class Avslag(
                    override val søknadsbehandling: DomeneSøknadsbehandling.Underkjent.Avslag,
                ) : Underkjent
            }

            sealed interface Iverksatt : Søknad {

                val vedtak: no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak

                data class Innvilget(
                    override val vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling,
                ) : Iverksatt {
                    override val søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget
                        get() = vedtak.behandling
                }

                data class Avslag(
                    override val vedtak: Avslagsvedtak,
                ) : Iverksatt {
                    override val søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag
                        get() = vedtak.behandling
                }
            }

            data class Lukket(
                override val søknadsbehandling: LukketSøknadsbehandling,
                // TODO jah: Erstatt med saksbehandler fra behandlinga hvis det blir implmentert.
                val saksbehandler: NavIdentBruker.Saksbehandler,
            ) : Søknad
        }

        sealed interface Revurdering : Behandling {
            val revurdering: no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering

            data class Opprettet(override val revurdering: OpprettetRevurdering) : Revurdering

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
                val vedtak: VedtakSomKanRevurderes.EndringIYtelse

                data class Innvilget(
                    override val vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering,
                ) : Iverksatt {
                    override val revurdering: IverksattRevurdering.Innvilget
                        get() = vedtak.behandling
                }

                data class Opphørt(
                    override val vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering,
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
                val vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse,
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
                val vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse,
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
                    get() = vedtak.klage
            }

            data class Avsluttet(override val klage: AvsluttetKlage) : Klage
        }
    }

    /**
     * Brukes til stønadsstatistikk
     */
    data class Stønadsvedtak(val vedtak: VedtakSomKanRevurderes.EndringIYtelse) : StatistikkEvent
}
