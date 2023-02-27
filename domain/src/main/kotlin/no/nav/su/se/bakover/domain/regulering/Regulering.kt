package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.grunnlag.SjekkOmGrunnlagErKonsistent
import no.nav.su.se.bakover.domain.grunnlag.erGyldigTilstand
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.time.Clock
import java.util.UUID

fun Regulering.inneholderAvslag(): Boolean =
    this.grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.vurdering is Vilkårsvurderingsresultat.Avslag

sealed interface Regulering : Reguleringsfelter {

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean

    companion object {
        /**
         * @param clock Brukes kun dersom [opprettet] ikke sendes inn.
         */
        fun opprettRegulering(
            id: UUID = UUID.randomUUID(),
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            clock: Clock,
            opprettet: Tidspunkt = Tidspunkt.now(clock),
            sakstype: Sakstype,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, OpprettetRegulering> {
            val reguleringstype = SjekkOmGrunnlagErKonsistent(gjeldendeVedtaksdata).resultat.fold(
                { konsistensproblemer ->
                    val message =
                        "Kunne ikke opprette regulering for saksnummer $saksnummer." +
                            " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
                    if (konsistensproblemer.erGyldigTilstand()) {
                        log.info(message)
                    } else {
                        log.error(message)
                    }
                    return LagerIkkeReguleringDaDenneUansettMåRevurderes.left()
                },
                {
                    gjeldendeVedtaksdata.utledReguleringstype()
                },
            )

            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstype,
                sakstype = sakstype,
            ).right()
        }
    }

    object LagerIkkeReguleringDaDenneUansettMåRevurderes
}
