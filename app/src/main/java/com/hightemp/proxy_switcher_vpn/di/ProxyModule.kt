package com.hightemp.proxy_switcher_vpn.di

import com.hightemp.proxy_switcher_vpn.proxy.AndroidProxyNetworkResolver
import com.hightemp.proxy_switcher_vpn.proxy.HttpsProxyInspector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyInspector
import com.hightemp.proxy_switcher_vpn.proxy.ProxyNetworkResolver
import com.hightemp.proxy_switcher_vpn.proxy.ProxyReachabilityTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTester
import com.hightemp.proxy_switcher_vpn.proxy.ProxyTunnelOpener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProxyModule {
    @Binds
    abstract fun bindProxyReachabilityTester(
        tester: ProxyTester
    ): ProxyReachabilityTester

    @Binds
    abstract fun bindProxyTunnelOpener(
        tester: ProxyTester
    ): ProxyTunnelOpener

    @Binds
    abstract fun bindProxyInspector(
        inspector: HttpsProxyInspector
    ): ProxyInspector

    @Binds
    abstract fun bindProxyNetworkResolver(
        resolver: AndroidProxyNetworkResolver
    ): ProxyNetworkResolver
}
