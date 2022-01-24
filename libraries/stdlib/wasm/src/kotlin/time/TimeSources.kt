/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package kotlin.time

@SinceKotlin("1.3")
@ExperimentalTime
internal actual object MonotonicTimeSource : TimeSource {
    actual override fun markNow(): DefaultTimeMark = TODO("Wasm stdlib: MonotonicTimeSource::markNow")
    actual fun elapsedFrom(timeMark: DefaultTimeMark): Duration = TODO("Wasm stdlib: MonotonicTimeSource")
    actual fun adjustReading(timeMark: DefaultTimeMark, duration: Duration): DefaultTimeMark = TODO("Wasm stdlib: MonotonicTimeSource")
}

internal actual class DefaultTimeMarkReading // TODO: Long?