package com.example.gles;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Left: a GLES2 textured cube (shaders, a VBO, a client-side index buffer,
 * GLUtils, Matrix, depth test). Right: a GLES1 triangle drawn through the GL10
 * interface. The GLES2 renderer checks GL state itself and clears to magenta
 * when a check fails, so the screenshot shows it.
 */
public class GlesActivity extends Activity {
    static final String TAG = "gles";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);

        GLSurfaceView cube = new GLSurfaceView(this);
        cube.setEGLContextClientVersion(2);
        cube.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        cube.setRenderer(new CubeRenderer());
        cube.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        root.addView(cube, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        GLSurfaceView tri = new GLSurfaceView(this);
        tri.setRenderer(new TriangleRenderer());
        tri.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        root.addView(tri, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        setContentView(root);
    }

    static FloatBuffer floats(float[] v) {
        FloatBuffer b = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(v).position(0);
        return b;
    }

    static class CubeRenderer implements GLSurfaceView.Renderer {
        private static final String VS = "uniform mat4 uMvp;\n"
                + "attribute vec3 aPos;\n"
                + "attribute vec2 aTex;\n"
                + "varying vec2 vTex;\n"
                + "void main() {\n"
                + "  vTex = aTex;\n"
                + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
                + "}\n";
        private static final String FS = "precision mediump float;\n"
                + "uniform sampler2D uTex;\n"
                + "varying vec2 vTex;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(uTex, vTex);\n"
                + "}\n";

        private final StringBuilder mFailures = new StringBuilder();
        private int mProgram;
        private int mVbo;
        private int mTexture;
        private ShortBuffer mIndices;
        private final float[] mProj = new float[16];
        private final float[] mView = new float[16];
        private final float[] mModel = new float[16];
        private final float[] mMvp = new float[16];

        private void expect(boolean ok, String what) {
            if (!ok) {
                mFailures.append(what).append("; ");
                Log.e(TAG, "GLES CHECK FAILED: " + what);
            }
        }

        private int shader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] status = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0);
            expect(status[0] == GLES20.GL_TRUE, "compile: " + GLES20.glGetShaderInfoLog(s));
            return s;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String version = GLES20.glGetString(GLES20.GL_VERSION);
            Log.i(TAG, "GL_VERSION " + version + ", GL_RENDERER " + GLES20.glGetString(GLES20.GL_RENDERER));
            expect(version != null && version.startsWith("OpenGL ES"), "GL_VERSION " + version);
            expect(!EGL14.eglGetCurrentContext().equals(EGL14.EGL_NO_CONTEXT), "eglGetCurrentContext");

            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, shader(GLES20.GL_VERTEX_SHADER, VS));
            GLES20.glAttachShader(mProgram, shader(GLES20.GL_FRAGMENT_SHADER, FS));
            GLES20.glBindAttribLocation(mProgram, 0, "aPos");
            GLES20.glBindAttribLocation(mProgram, 1, "aTex");
            GLES20.glLinkProgram(mProgram);
            int[] status = new int[1];
            GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, status, 0);
            expect(status[0] == GLES20.GL_TRUE, "link: " + GLES20.glGetProgramInfoLog(mProgram));
            int[] size = new int[1];
            int[] type = new int[1];
            String a0 = GLES20.glGetActiveAttrib(mProgram, 0, size, 0, type, 0);
            String a1 = GLES20.glGetActiveAttrib(mProgram, 1, size, 0, type, 0);
            expect(("aPos".equals(a0) && "aTex".equals(a1)) || ("aTex".equals(a0) && "aPos".equals(a1)),
                    "glGetActiveAttrib " + a0 + "," + a1);

            // 6 faces x 4 vertices: x, y, z, s, t
            float[] v = new float[6 * 4 * 5];
            int[][] faces = {{0, 1}, {0, -1}, {1, 1}, {1, -1}, {2, 1}, {2, -1}};
            int k = 0;
            for (int[] f : faces) {
                int axis = f[0];
                float sign = f[1];
                float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (float[] c : corners) {
                    float[] p = new float[3];
                    p[axis] = sign;
                    p[(axis + 1) % 3] = c[0];
                    p[(axis + 2) % 3] = c[1];
                    v[k++] = p[0];
                    v[k++] = p[1];
                    v[k++] = p[2];
                    v[k++] = (c[0] + 1) / 2;
                    v[k++] = (c[1] + 1) / 2;
                }
            }
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            mVbo = ids[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.length * 4, floats(v), GLES20.GL_STATIC_DRAW);
            short[] idx = new short[36];
            for (int f = 0, i = 0; f < 6; f++) {
                short b = (short) (f * 4);
                idx[i++] = b;
                idx[i++] = (short) (b + 1);
                idx[i++] = (short) (b + 2);
                idx[i++] = b;
                idx[i++] = (short) (b + 2);
                idx[i++] = (short) (b + 3);
            }
            mIndices = ByteBuffer.allocateDirect(idx.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
            mIndices.put(idx).position(0);

            // amber texture with a dark border, drawn with Canvas and uploaded with GLUtils
            Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(0xFF263238);
            Paint p = new Paint();
            p.setColor(0xFFFFC107);
            c.drawRect(4, 4, 60, 60, p);
            GLES20.glGenTextures(1, ids, 0);
            mTexture = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

            // Matrix sanity: M * inverse(M) == identity
            float[] m = new float[16];
            float[] inv = new float[16];
            float[] prod = new float[16];
            Matrix.setRotateM(m, 0, 33, 0.3f, 1, 0.2f);
            Matrix.translateM(m, 0, 1, 2, 3);
            Matrix.scaleM(m, 0, 2, 2, 2);
            expect(Matrix.invertM(inv, 0, m, 0), "invertM");
            Matrix.multiplyMM(prod, 0, m, 0, inv, 0);
            for (int i = 0; i < 16; i++) {
                float want = (i % 5 == 0) ? 1 : 0;
                if (Math.abs(prod[i] - want) > 1e-4f) {
                    expect(false, "Matrix M*inv(M)[" + i + "]=" + prod[i]);
                    break;
                }
            }
            expect(GLES20.glGetError() == GLES20.GL_NO_ERROR, "glGetError after setup");
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            Matrix.perspectiveM(mProj, 0, 45, (float) width / height, 1, 20);
            Matrix.setLookAtM(mView, 0, 0, 0, 7, 0, 0, 0, 0, 1, 0);
            Log.i(TAG, "cube surface " + width + "x" + height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            boolean failed = mFailures.length() > 0;
            GLES20.glClearColor(failed ? 1f : 0.1f, failed ? 0f : 0.1f, failed ? 1f : 0.3f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glUseProgram(mProgram);
            Matrix.setIdentityM(mModel, 0);
            Matrix.rotateM(mModel, 0, 20, 0, 1, 0);
            Matrix.rotateM(mModel, 0, 15, 1, 0, 0);
            Matrix.multiplyMM(mMvp, 0, mView, 0, mModel, 0);
            Matrix.multiplyMM(mMvp, 0, mProj, 0, mMvp, 0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(mProgram, "uMvp"), 1, false, mMvp, 0);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uTex"), 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 20, 0);
            GLES20.glEnableVertexAttribArray(1);
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 20, 12);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, mIndices);
            int err = GLES20.glGetError();
            if (err != GLES20.GL_NO_ERROR && !failed) {
                expect(false, "glGetError after draw 0x" + Integer.toHexString(err));
            }
            Log.i(TAG, "cube frame drawn" + (failed ? " (failed: " + mFailures + ")" : ""));
        }
    }

    static class TriangleRenderer implements GLSurfaceView.Renderer {
        private final FloatBuffer mVertices = floats(new float[] {-0.6f, -0.6f, 0.6f, -0.6f, 0f, 0.7f});

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "GLES1 GL_VERSION " + gl.glGetString(GL10.GL_VERSION));
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            gl.glViewport(0, 0, width, height);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glOrthof(-1, 1, -1, 1, -1, 1);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            gl.glClearColor(0.2f, 0f, 0f, 1f);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glVertexPointer(2, GL10.GL_FLOAT, 0, mVertices);
            gl.glColor4f(0f, 0xC8 / 255f, 0x53 / 255f, 1f);
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 3);
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
            Log.i(TAG, "triangle frame drawn, glGetError " + gl.glGetError());
        }
    }
}
