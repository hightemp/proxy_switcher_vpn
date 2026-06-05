package com.hightemp.proxy_switcher_vpn.di

import com.hightemp.proxy_switcher_vpn.vpn.engine.LibboxVpnEngine
import com.hightemp.proxy_switcher_vpn.vpn.engine.VpnEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnEngineModule {
    @Binds
    @Singleton
    abstract fun bindVpnEngine(engine: LibboxVpnEngine): VpnEngine
}
