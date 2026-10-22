package com.clipboost.app.pipeline

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/*
 * Classic (non-AI) filters, run on the phone GPU inside Media3's effect pipeline.
 * The UI labels them "Classic" — they are never presented as AI.
 */

/** Edge-preserving 5×5 bilateral denoise. [rangeSigma] is on the 0..1 colour scale. */
@UnstableApi
class DenoiseEffect(private val rangeSigma: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FilterShaderProgram(useHdr, DENOISE_FRAGMENT) { it.setFloatUniform("uRangeSigma", rangeSigma) }
}

/** Unsharp mask with a per-pixel cap on the added detail, so edges don't get halos. */
@UnstableApi
class SharpenEffect(private val amount: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FilterShaderProgram(useHdr, SHARPEN_FRAGMENT) { it.setFloatUniform("uAmount", amount) }
}

/** Single-pass, same-size filter: one input texture in, one output texture out. */
@UnstableApi
private class FilterShaderProgram(
    useHdr: Boolean,
    fragmentShader: String,
    private val bindUniforms: (GlProgram) -> Unit,
) : BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram = try {
        GlProgram(VERTEX_SHADER, fragmentShader)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texelSize = floatArrayOf(0f, 0f)

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelSize = floatArrayOf(1f / inputWidth, 1f / inputHeight)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.setFloatsUniform("uTexelSize", texelSize)
            bindUniforms(glProgram)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

private const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

private const val DENOISE_FRAGMENT = PRECISION + """
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uRangeSigma;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
  float inv2r = 1.0 / (2.0 * uRangeSigma * uRangeSigma);
  vec3 sum = vec3(0.0);
  float wsum = 0.0;
  for (int dy = -2; dy <= 2; dy++) {
    for (int dx = -2; dx <= 2; dx++) {
      vec2 offset = vec2(float(dx), float(dy)) * uTexelSize;
      vec3 c = texture2D(uTexSampler, vTexSamplingCoord + offset).rgb;
      vec3 d = c - center.rgb;
      // Spatial sigma 1.5 px; range weight keeps edges from being smeared.
      float w = exp(-float(dx * dx + dy * dy) / 4.5) * exp(-dot(d, d) * inv2r);
      sum += c * w;
      wsum += w;
    }
  }
  gl_FragColor = vec4(sum / wsum, center.a);
}
"""

private const val SHARPEN_FRAGMENT = PRECISION + """
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uAmount;
varying vec2 vTexSamplingCoord;
const float HALO_LIMIT = 0.06;
void main() {
  vec2 t = uTexelSize;
  vec2 p = vTexSamplingCoord;
  vec4 c = texture2D(uTexSampler, p);
  vec3 blur = c.rgb * 4.0
    + (texture2D(uTexSampler, p + vec2(t.x, 0.0)).rgb + texture2D(uTexSampler, p - vec2(t.x, 0.0)).rgb
     + texture2D(uTexSampler, p + vec2(0.0, t.y)).rgb + texture2D(uTexSampler, p - vec2(0.0, t.y)).rgb) * 2.0
    + texture2D(uTexSampler, p + t).rgb + texture2D(uTexSampler, p - t).rgb
    + texture2D(uTexSampler, p + vec2(t.x, -t.y)).rgb + texture2D(uTexSampler, p + vec2(-t.x, t.y)).rgb;
  blur /= 16.0;
  vec3 detail = clamp((c.rgb - blur) * uAmount, -HALO_LIMIT, HALO_LIMIT);
  gl_FragColor = vec4(max(c.rgb + detail, 0.0), c.a);
}
"""
