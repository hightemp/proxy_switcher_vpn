package com.hightemp.proxy_switcher_vpn.di

import com.hightemp.proxy_switcher_vpn.vpn.engine.LibboxVpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.stats.VpnStatsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnEngineModule {
    @Binds
    @Singleton
    abstract fun bindVpnEngine(engine: LibboxVpnEngine): VpnEngine

    companion object {
        @Provides
        @Singleton
        fun provideVpnStatsStore(): VpnStatsStore = VpnStatsStore()
    }
}
