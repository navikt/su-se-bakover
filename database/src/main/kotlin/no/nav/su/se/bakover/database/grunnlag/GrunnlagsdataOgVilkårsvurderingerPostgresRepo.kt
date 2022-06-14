package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.domain.Sakstype
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
    private val pensjonVilkårsvurderingPostgresRepo: PensjonVilkårsvurderingPostgresRepo,
) {
    fun lagre(
        behandlingId: UUID,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreGrunnlagsdataOgVilkårsvurderinger") {
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføreVilkår()
                .tap {
                    uføreVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = behandlingId,
                        vilkår = it,
                        tx = tx,
                    )
                }

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
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.pensjonsVilkår()
                .tap {
                    pensjonVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = behandlingId,
                        vilkår = it,
                        tx = tx,
                    )
                }
        }
    }

    fun hentForRevurdering(
        behandlingId: UUID,
        session: Session,
        sakstype: Sakstype,
    ): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForRevurderingId") {
            val grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(behandlingId, session),
            )
            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    Vilkårsvurderinger.Revurdering.Alder(
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }
                Sakstype.UFØRE -> {
                    Vilkårsvurderinger.Revurdering.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }
    }

    fun hentForSøknadsbehandling(
        behandlingId: UUID,
        session: Session,
        sakstype: Sakstype,
    ): GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForSøknadsbehandlingId") {
            val grunnlagsdata = Grunnlagsdata.createTillatUfullstendigBosituasjon(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(behandlingId, session),
            )

            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    Vilkårsvurderinger.Søknadsbehandling.Alder(
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }
                Sakstype.UFØRE -> {
                    Vilkårsvurderinger.Søknadsbehandling.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }
    }
}
