package com.hightemp.proxy_switcher_vpn.di

import com.hightemp.proxy_switcher_vpn.vpn.background.AndroidBackgroundRestrictionChecker
import com.hightemp.proxy_switcher_vpn.vpn.background.AndroidProcessExitHistory
import com.hightemp.proxy_switcher_vpn.vpn.background.BackgroundRestrictionChecker
import com.hightemp.proxy_switcher_vpn.vpn.background.ProcessExitHistory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundModule {
    @Binds
    abstract fun bindBackgroundRestrictionChecker(
        checker: AndroidBackgroundRestrictionChecker
    ): BackgroundRestrictionChecker

    @Binds
    abstract fun bindProcessExitHistory(
        history: AndroidProcessExitHistory
    ): ProcessExitHistory
}
