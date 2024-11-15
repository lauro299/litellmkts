package org.litellmkt.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module()
@ComponentScan("org.litellmkt")
class LibModule

fun getLitellmktModule():org.koin.core.module.Module = LibModule().module