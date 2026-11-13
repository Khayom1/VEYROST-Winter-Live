package com.veyrost.winterlive

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.Matrix
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WinterWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WinterEngine()

    inner class WinterEngine : Engine() {
        private var renderer: WinterRenderer? = null
        private var renderThread: Thread? = null
        @Volatile private var visible = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            renderer = WinterRenderer()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) startRenderer() else stopRenderer()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.resize(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stopRenderer()
            super.onSurfaceDestroyed(holder)
        }

        override fun onTouchEvent(event: MotionEvent) {
            renderer?.onTouch(event.x, event.y, event.action, event.pointerCount)
            super.onTouchEvent(event)
        }

        override fun onDestroy() {
            visible = false
            stopRenderer()
            renderer?.release()
            renderer = null
            super.onDestroy()
        }

        private fun startRenderer() {
            if (renderThread?.isAlive == true) return
            val engine = renderer ?: return
            renderThread = Thread {
                if (!engine.open(surfaceHolder.surface)) return@Thread
                var last = System.nanoTime()
                while (visible && !Thread.currentThread().isInterrupted && surfaceHolder.surface.isValid) {
                    val now = System.nanoTime()
                    val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
                    last = now
                    engine.draw(dt)
                    try { Thread.sleep(16L) } catch (_: InterruptedException) { break }
                }
                engine.close()
            }.apply { name = "VEYROST-3D-Renderer"; start() }
        }

        private fun stopRenderer() {
            renderThread?.interrupt()
            try { renderThread?.join(700) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            renderThread = null
        }
    }
}

private class WinterRenderer {
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var width = 1
    private var height = 1
    private var program = 0
    private var aPosition = 0
    private var aNormal = 1
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLight = 0
    private var uPointSize = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var elapsed = 0f
    private var touchActive = false
    private var touchX = 0f
    private var touchY = 0f
    private var rollAmount = 0f
    private var snowmanParts = 0
    private var rollingX = 0f
    private var rollingZ = 2.3f

    private val sphere = Mesh.sphere(18, 24)
    private val ground = Mesh.plane(30f)
    private val cone = Mesh.cone(24)
    private val trunk = Mesh.cylinder(12)
    private val mountains = Mesh.mountain()
    private val flakes = Array(180) {
        Flake(
            Random.nextFloat() * 18f - 9f,
            Random.nextFloat() * 10f + 1f,
            Random.nextFloat() * 15f - 5f,
            Random.nextFloat() * 0.8f + 0.35f,
            Random.nextFloat() * 0.025f + 0.012f
        )
    }

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 48f, width.toFloat() / height, 0.1f, 80f)
    }

    fun onTouch(x: Float, y: Float, action: Int, pointers: Int) {
        if (pointers > 1) return
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchActive = true
                touchX = x / width.toFloat()
                touchY = y / height.toFloat()
                rollAmount = 0f
                rollingX = (touchX - 0.5f) * 7f
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchActive) {
                    val nx = x / width.toFloat()
                    val ny = y / height.toFloat()
                    rollAmount = (rollAmount + kotlin.math.abs(nx - touchX) * 2.2f + kotlin.math.abs(ny - touchY) * 0.6f).coerceAtMost(1f)
                    touchX = nx
                    touchY = ny
                    rollingX = (nx - 0.5f) * 7f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchActive) {
                    if (rollAmount > 0.08f && snowmanParts < 3) snowmanParts++
                    touchActive = false
                    rollAmount = 0f
                }
            }
        }
    }

    fun open(nativeSurface: android.view.Surface): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val versions = IntArray(2)
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return false

        val configAttrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0) || count[0] == 0) return false

        val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], nativeSurface, intArrayOf(EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) return false

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) return false
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
        uPointSize = GLES20.glGetUniformLocation(program, "uPointSize")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        return true
    }

    fun draw(dt: Float) {
        elapsed += dt
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.035f, 0.075f, 0.14f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 4.4f, 11.8f, 0f, 1.0f, -1.8f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        GLES20.glUniform3f(uLight, -0.45f, 0.85f, 0.38f)
        GLES20.glUniform1f(uPointSize, 3.0f)

        // Layered mountain range in the far distance.
        drawMesh(mountains, -0.2f, 1.5f, -12f, 15f, 4.5f, 1f, 0.40f, 0.58f, 0.72f)
        drawMesh(mountains, 3.5f, 1.0f, -10f, 11f, 3.2f, 1f, 0.52f, 0.69f, 0.80f)

        // Snow-covered ground plane.
        drawMesh(ground, 0f, -0.12f, 0f, 1f, 1f, 1f, 0.83f, 0.91f, 0.97f)

        // A forest with trunks, layered conifer crowns and snow caps.
        for (i in -5..5) {
            val x = i * 1.42f + if (i % 2 == 0) 0.22f else -0.18f
            val z = -3.5f - (i % 3) * 1.0f
            val scale = 0.82f + ((i * i) % 4) * 0.12f
            drawMesh(trunk, x, 0.55f * scale, z, 0.16f * scale, 1.1f * scale, 0.16f * scale, 0.22f, 0.16f, 0.13f)
            drawMesh(cone, x, 1.45f * scale, z, 0.92f * scale, 1.3f * scale, 0.92f * scale, 0.035f, 0.18f, 0.22f)
            drawMesh(cone, x, 2.12f * scale, z, 0.69f * scale, 1.08f * scale, 0.69f * scale, 0.045f, 0.25f, 0.29f)
            drawMesh(cone, x, 2.68f * scale, z, 0.43f * scale, 0.9f * scale, 0.43f * scale, 0.06f, 0.30f, 0.34f)
            drawMesh(cone, x, 1.72f * scale, z + 0.025f, 0.82f * scale, 1.31f * scale, 0.82f * scale, 0.76f, 0.87f, 0.93f, 0.48f)
            drawMesh(cone, x, 2.39f * scale, z + 0.025f, 0.58f * scale, 1.08f * scale, 0.58f * scale, 0.88f, 0.94f, 0.98f, 0.58f)
        }

        // Distant snow drifts.
        for (i in -3..3) {
            drawMesh(sphere, i * 2.5f, 0.05f, -5.4f, 1.8f, 0.24f, 1.2f, 0.91f, 0.96f, 0.99f)
        }

        // Snowman is assembled from exactly three rolled snowballs.
        if (snowmanParts >= 1) drawMesh(sphere, 0f, 0.62f, 1.1f, 0.68f, 0.68f, 0.68f, 0.96f, 0.985f, 1f)
        if (snowmanParts >= 2) drawMesh(sphere, 0f, 1.58f, 1.1f, 0.49f, 0.49f, 0.49f, 0.98f, 0.995f, 1f)
        if (snowmanParts >= 3) {
            drawMesh(sphere, 0f, 2.28f, 1.1f, 0.34f, 0.34f, 0.34f, 1f, 1f, 1f)
            drawMesh(sphere, -0.12f, 2.36f, 0.79f, 0.035f, 0.035f, 0.035f, 0.035f, 0.045f, 0.06f)
            drawMesh(sphere, 0.12f, 2.36f, 0.79f, 0.035f, 0.035f, 0.035f, 0.035f, 0.045f, 0.06f)
            drawMesh(cone, 0f, 2.22f, 0.73f, 0.10f, 0.40f, 0.10f, 0.95f, 0.31f, 0.08f)
            drawMesh(sphere, 0f, 2.72f, 1.1f, 0.40f, 0.12f, 0.40f, 0.035f, 0.06f, 0.11f)
        }

        if (touchActive) {
            val bob = 0.34f + rollAmount * 0.32f
            drawMesh(sphere, rollingX, bob, rollingZ, 0.20f + rollAmount * 0.24f, 0.20f + rollAmount * 0.24f, 0.20f + rollAmount * 0.24f, 0.99f, 0.998f, 1f)
        }

        drawSnow(dt)
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    private fun drawSnow(dt: Float) {
        // Soft drifting flakes rendered as depth-aware points.
        val values = FloatArray(flakes.size * 6)
        var p = 0
        for (flake in flakes) {
            flake.y -= flake.speed * dt
            flake.x += sin((elapsed * 0.55f + flake.z).toDouble()).toFloat() * dt * 0.18f
            if (flake.y < 0.05f) {
                flake.y = Random.nextFloat() * 7f + 5f
                flake.x = Random.nextFloat() * 18f - 9f
                flake.z = Random.nextFloat() * 15f - 5f
            }
            values[p++] = flake.x
            values[p++] = flake.y
            values[p++] = flake.z
            values[p++] = 0f
            values[p++] = 1f
            values[p++] = 0f
        }
        val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(values).position(0)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4f(uColor, 0.93f, 0.98f, 1f, 0.9f)
        GLES20.glUniform1f(uPointSize, (width / 180f).coerceIn(2.0f, 5.5f))
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 24, buffer)
        buffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, flakes.size)
    }

    private fun drawMesh(
        mesh: Mesh, x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, alpha: Float = 1f
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4f(uColor, r, g, b, alpha)
        GLES20.glUniform1f(uPointSize, 1f)
        mesh.draw(aPosition, aNormal)
    }

    fun close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    fun release() = close()

    private fun createProgram(vertex: String, fragment: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        if (vs == 0 || fs == 0) return 0
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            val status = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                GLES20.glDeleteProgram(it)
                GLES20.glDeleteShader(vs)
                GLES20.glDeleteShader(fs)
            }
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private data class Flake(var x: Float, var y: Float, var z: Float, val speed: Float, val size: Float)

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform float uPointSize;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                vNormal = normalize(mat3(uModel) * aNormal);
                vHeight = aPosition.y;
                gl_PointSize = uPointSize;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLight;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                if (gl_PointCoord.x >= 0.0 && gl_PointCoord.y >= 0.0) {
                    float d = distance(gl_PointCoord, vec2(0.5));
                    if (d > 0.5) discard;
                }
                float diffuse = max(dot(normalize(vNormal), normalize(uLight)), 0.0);
                float light = 0.58 + diffuse * 0.42;
                gl_FragColor = vec4(uColor.rgb * light, uColor.a);
            }
        """
    }
}

private class Mesh(private val data: FloatArray, private val vertexCount: Int) {
    private val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }

    fun draw(position: Int, normal: Int) {
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, buffer)
        buffer.position(3)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    companion object {
        fun sphere(stacks: Int, slices: Int): Mesh {
            val out = ArrayList<Float>()
            fun point(phi: Double, theta: Double): FloatArray {
                val x = (cos(phi) * sin(theta)).toFloat()
                val y = cos(theta).toFloat()
                val z = (sin(phi) * sin(theta)).toFloat()
                return floatArrayOf(x, y, z)
            }
            fun vertex(v: FloatArray) { out.add(v[0]); out.add(v[1]); out.add(v[2]); out.add(v[0]); out.add(v[1]); out.add(v[2]) }
            for (i in 0 until stacks) {
                val t0 = Math.PI * i / stacks
                val t1 = Math.PI * (i + 1) / stacks
                for (j in 0 until slices) {
                    val p0 = 2.0 * Math.PI * j / slices
                    val p1 = 2.0 * Math.PI * (j + 1) / slices
                    val a = point(p0, t0); val b = point(p1, t0); val c = point(p1, t1); val d = point(p0, t1)
                    vertex(a); vertex(b); vertex(c); vertex(a); vertex(c); vertex(d)
                }
            }
            return Mesh(out.toFloatArray(), out.size / 6)
        }

        fun cone(segments: Int): Mesh {
            val out = ArrayList<Float>()
            for (i in 0 until segments) {
                val a = 2.0 * Math.PI * i / segments
                val b = 2.0 * Math.PI * (i + 1) / segments
                val x1 = cos(a).toFloat(); val z1 = sin(a).toFloat()
                val x2 = cos(b).toFloat(); val z2 = sin(b).toFloat()
                add(out, 0f, 1f, 0f, 0f, 1f, 0f)
                add(out, x1, -1f, z1, x1, 0.7f, z1)
                add(out, x2, -1f, z2, x2, 0.7f, z2)
                add(out, 0f, -1f, 0f, 0f, -1f, 0f)
                add(out, x2, -1f, z2, 0f, -1f, 0f)
                add(out, x1, -1f, z1, 0f, -1f, 0f)
            }
            return Mesh(out.toFloatArray(), out.size / 6)
        }

        fun cylinder(segments: Int): Mesh {
            val out = ArrayList<Float>()
            for (i in 0 until segments) {
                val a = 2.0 * Math.PI * i / segments
                val b = 2.0 * Math.PI * (i + 1) / segments
                val x1 = cos(a).toFloat(); val z1 = sin(a).toFloat()
                val x2 = cos(b).toFloat(); val z2 = sin(b).toFloat()
                add(out, x1,-1f,z1,x1,0f,z1); add(out,x1,1f,z1,x1,0f,z1); add(out,x2,1f,z2,x2,0f,z2)
                add(out,x1,-1f,z1,x1,0f,z1); add(out,x2,1f,z2,x2,0f,z2); add(out,x2,-1f,z2,x2,0f,z2)
            }
            return Mesh(out.toFloatArray(), out.size / 6)
        }

        fun plane(size: Float): Mesh {
            val s = size / 2f
            val out = floatArrayOf(
                -s,0f,-s, 0f,1f,0f,  s,0f,-s, 0f,1f,0f,  s,0f,s, 0f,1f,0f,
                -s,0f,-s, 0f,1f,0f,  s,0f,s, 0f,1f,0f, -s,0f,s, 0f,1f,0f
            )
            return Mesh(out, 6)
        }

        fun mountain(): Mesh {
            val out = ArrayList<Float>()
            val points = arrayOf(
                floatArrayOf(-1f,-1f,0f), floatArrayOf(0f,1f,0f), floatArrayOf(1f,-1f,0f),
                floatArrayOf(-1f,-1f,-1f), floatArrayOf(0f,1f,0f), floatArrayOf(-1f,-1f,1f),
                floatArrayOf(1f,-1f,1f), floatArrayOf(0f,1f,0f), floatArrayOf(1f,-1f,-1f),
                floatArrayOf(-1f,-1f,1f), floatArrayOf(0f,1f,0f), floatArrayOf(1f,-1f,1f)
            )
            for (p in points) add(out,p[0],p[1],p[2],0f,0f,1f)
            return Mesh(out.toFloatArray(), out.size / 6)
        }

        private fun add(out: ArrayList<Float>, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            out.add(x); out.add(y); out.add(z); out.add(nx); out.add(ny); out.add(nz)
        }
    }
}
