package com.m3sv.droidupnp.di

import android.app.Application
import android.content.Context
import com.m3sv.droidupnp.di.scope.ApplicationScope
import com.m3sv.droidupnp.upnp.UPnPManager
import dagger.Module
import dagger.Provides
import org.droidupnp.model.upnp.Factory


@Module
@ApplicationScope
class AppModule {

    @Provides
    fun provideContext(app: Application): Context = app.applicationContext

    @Provides
    @ApplicationScope
    fun provideUPnPManager(context: Context, factory: Factory) =
        UPnPManager(factory.createUpnpServiceController(context), factory)
}