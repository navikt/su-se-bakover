package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.common.domain.Vurdering
import vilkår.vurderinger.domain.erGyldigTilstand
import java.time.Clock
import java.util.UUID

fun Regulering.inneholderAvslag(): Boolean = this.vilkårsvurderinger.resultat() is Vurdering.Avslag

sealed interface Regulering : Reguleringsfelter {

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean

    companion object {

        private val log: Logger = LoggerFactory.getLogger(this::class.java)

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
            val reguleringstype =
                gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent().fold(
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

    data object LagerIkkeReguleringDaDenneUansettMåRevurderes
}
