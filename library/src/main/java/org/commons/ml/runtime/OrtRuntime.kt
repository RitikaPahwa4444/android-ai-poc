package org.commons.ml.runtime

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/** Infrastructure boundary around ONNX Runtime and application-owned model assets. */
internal class OrtRuntime(private val context: Context) {
    val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun openSession(assetName: String): OrtSession {
        val modelBytes = context.assets.open(assetName).use { it.readBytes() }
        return environment.createSession(modelBytes, OrtSession.SessionOptions())
    }
}
