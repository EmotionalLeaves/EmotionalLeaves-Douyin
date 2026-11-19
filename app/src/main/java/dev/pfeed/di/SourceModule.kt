package dev.pfeed.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.pfeed.source.SourceAdapter
import dev.pfeed.source.direct.DirectMediaAdapter
import dev.pfeed.source.douyin.DouyinAdapter
import dev.pfeed.source.local.LocalFilesAdapter

/** Register new adapters here (one @Binds @IntoSet line each). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {
    @Binds @IntoSet
    abstract fun douyin(adapter: DouyinAdapter): SourceAdapter

    @Binds @IntoSet
    abstract fun local(adapter: LocalFilesAdapter): SourceAdapter

    @Binds @IntoSet
    abstract fun direct(adapter: DirectMediaAdapter): SourceAdapter
}
