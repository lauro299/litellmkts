package org.litellmkt.di

import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module(
    createdAtStart = true,
    includes = [
        HandlersModule::class,
        NetworkModule::class
    ]
)
class LibModule

fun getLitellmktModule(): org.koin.core.module.Module = LibModule().module