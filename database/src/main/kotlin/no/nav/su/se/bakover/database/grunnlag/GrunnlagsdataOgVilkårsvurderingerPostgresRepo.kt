package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

internal class GrunnlagsdataOgVilkårsvurderingerPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val bosituasjongrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val uføreVilkårsvurderingPostgresRepo: UføreVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingPostgresRepo: FormueVilkårsvurderingPostgresRepo,
    private val utenlandsoppholdVilkårsvurderingPostgresRepo: UtenlandsoppholdVilkårsvurderingPostgresRepo,
    private val opplysningspliktVilkårsvurderingPostgresRepo: OpplysningspliktVilkårsvurderingPostgresRepo,
) {
    fun lagre(
        behandlingId: UUID,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreGrunnlagsdataOgVilkårsvurderinger") {
            uføreVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføreVilkår(),
                tx = tx,
            )

            formueVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.formueVilkår(),
                tx = tx,
            )

            utenlandsoppholdVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.utenlandsoppholdVilkår(),
                tx = tx,
            )
            bosituasjongrunnlagPostgresRepo.lagreBosituasjongrunnlag(
                behandlingId = behandlingId,
                grunnlag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon,
                tx = tx,
            )
            fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(
                behandlingId = behandlingId,
                fradragsgrunnlag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
                tx = tx,
            )
            opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.opplysningspliktVilkår(),
                tx = tx,
            )
        }
    }

    fun hentForRevurdering(
        behandlingId: UUID,
        session: Session,
    ): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForRevurderingId") {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                    bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(behandlingId, session),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                    uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                ),
            )
        }
    }

    fun hentForSøknadsbehandling(
        behandlingId: UUID,
        session: Session,
    ): GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForSøknadsbehandlingId") {
            GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = Grunnlagsdata.createTillatUfullstendigBosituasjon(
                    fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                    bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(behandlingId, session),
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.Uføre(
                    uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                ),
            )
        }
    }
}
