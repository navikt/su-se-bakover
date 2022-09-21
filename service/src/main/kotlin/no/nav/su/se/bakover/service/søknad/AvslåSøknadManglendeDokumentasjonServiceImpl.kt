package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class AvslåSøknadManglendeDokumentasjonServiceImpl(
    private val clock: Clock,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val vedtakService: VedtakService,
    private val oppgaveService: OppgaveService,
    private val brevService: BrevService,
    private val sessionFactory: SessionFactory,
    private val sakService: SakService,
    private val satsFactory: SatsFactory,
) : AvslåSøknadManglendeDokumentasjonService {
    override fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Sak> {
        return søknadsbehandlingService.hentForSøknad(request.søknadId)
            ?.let { harBehandlingFraFør(request, it) }
            ?: opprettNyBehandlingFørst(request)
    }

    private fun harBehandlingFraFør(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        return avslå(request, søknadsbehandling)
    }

    private fun opprettNyBehandlingFørst(
        request: AvslåManglendeDokumentasjonRequest,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        val sak = sakService.hentSakForSøknad(request.søknadId)
            .getOrHandle { return KunneIkkeAvslåSøknad.FantIkkeSak.left() }

        val søknad = sak.hentSøknad(request.søknadId)
            .getOrHandle { return KunneIkkeAvslåSøknad.FantIkkeSøknad.left() }

        return søknadsbehandlingService.opprett(
            request = SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
                sakId = sak.id,
            ),
        ).mapLeft {
            KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling(it)
        }.map {
            return avslå(request, it)
        }
    }

    private fun avslå(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        /**
         * //TODO skulle ideelt sett at denne bare kunne bruke søknadsbehandlingservice for å få utført disse oppgavene, bør i såfall få plass transkasjoner på tvers av servicer.
         */
        val avslåttSøknadsbehandling = søknadsbehandling
            .leggTilStønadsperiodeHvisNull()
            .avslåPgaManglendeDok()
            .tilAttestering(
                saksbehandler = request.saksbehandler,
                fritekstTilBrev = request.fritekstTilBrev,
            )
            .tilIverksatt(
                attestering = Attestering.Iverksatt(
                    attestant = NavIdentBruker.Attestant(request.saksbehandler.navIdent),
                    opprettet = Tidspunkt.now(clock),
                ),
            )

        val avslag = AvslagManglendeDokumentasjon(avslåttSøknadsbehandling)

        val avslagsvedtak: Avslagsvedtak.AvslagVilkår = Avslagsvedtak.fromAvslagManglendeDokumentasjon(
            avslag = avslag,
            clock = clock,
        )

        val dokument = brevService.lagDokument(avslagsvedtak).getOrHandle {
            return KunneIkkeAvslåSøknad.KunneIkkeLageDokument(it).left()
        }.leggTilMetadata(
            metadata = Dokument.Metadata(
                sakId = avslagsvedtak.behandling.sakId,
                søknadId = avslagsvedtak.behandling.søknad.id,
                vedtakId = avslagsvedtak.id,
                bestillBrev = true,
            ),
        )

        sessionFactory.withTransactionContext { tx ->
            søknadsbehandlingService.lagre(avslag, tx)
            vedtakService.lagre(avslagsvedtak, tx)
            brevService.lagreDokument(dokument, tx)
        }

        oppgaveService.lukkOppgave(avslag.søknadsbehandling.oppgaveId)
            .mapLeft {
                log.warn("Klarte ikke å lukke oppgave for søknadsbehandling: ${avslag.søknadsbehandling.id}, feil:$it")
            }

        return sakService.hentSak(avslagsvedtak.behandling.sakId)
            .mapLeft { KunneIkkeAvslåSøknad.FantIkkeSak }
    }

    private fun Søknadsbehandling.leggTilStønadsperiodeHvisNull(): Søknadsbehandling {
        return oppdaterStønadsperiode(
            oppdatertStønadsperiode = stønadsperiode
                ?: Stønadsperiode.create(
                    periode = Periode.create(
                        fraOgMed = LocalDate.now(clock).startOfMonth(),
                        tilOgMed = LocalDate.now(clock).endOfMonth(),
                    ),
                ),
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            clock = clock,
        ).getOrHandle { throw IllegalArgumentException(it.toString()) }
    }

    private fun Søknadsbehandling.avslåPgaManglendeDok(): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return leggTilOpplysningspliktVilkår(
            opplysningspliktVilkår = OpplysningspliktVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeOpplysningsplikt.create(
                        id = UUID.randomUUID(),
                        opprettet = opprettet,
                        periode = periode,
                        grunnlag = Opplysningspliktgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = opprettet,
                            periode = periode,
                            beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                        ),
                    ),
                ),
            ).getOrHandle { throw IllegalArgumentException(it.toString()) },
        ).getOrHandle { throw IllegalArgumentException(it.toString()) } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }
}
