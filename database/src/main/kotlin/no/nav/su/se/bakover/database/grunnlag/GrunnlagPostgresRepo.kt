package no.nav.su.se.bakover.database.grunnlag

internal class GrunnlagPostgresRepo(
    private val uføregrunnlagRepo: UføregrunnlagRepo
) : GrunnlagRepo,
    UføregrunnlagRepo by uføregrunnlagRepo
