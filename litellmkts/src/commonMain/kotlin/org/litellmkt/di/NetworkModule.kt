package org.litellmkt.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("org.litellmkt.network")
class NetworkModule