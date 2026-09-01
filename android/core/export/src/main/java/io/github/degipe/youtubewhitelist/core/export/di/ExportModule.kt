package io.github.degipe.youtubewhitelist.core.export.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.degipe.youtubewhitelist.core.export.ExportImportService
import io.github.degipe.youtubewhitelist.core.export.ExportImportServiceImpl
import io.github.degipe.youtubewhitelist.core.export.content.AssetStarterPackSource
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackService
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackServiceImpl
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindExportImportService(
        impl: ExportImportServiceImpl
    ): ExportImportService

    @Binds
    @Singleton
    abstract fun bindStarterPackSource(
        impl: AssetStarterPackSource
    ): StarterPackSource

    @Binds
    @Singleton
    abstract fun bindStarterPackService(
        impl: StarterPackServiceImpl
    ): StarterPackService
}
