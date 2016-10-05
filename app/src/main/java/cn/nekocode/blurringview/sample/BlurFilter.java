package cn.nekocode.blurringview.sample;

import android.content.Context;
import android.content.res.Resources;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author nekocode (nekocode.cn@gmail.com)
 */
final class BlurFilter {
    private static final String VERTEX_SHADER = "attribute vec2  vPosition;\n" +
            "attribute vec2  vTexCoord;\n" +
            "varying vec2    texCoord;\n" +
            "\n" +
            "void main() {\n" +
            "    texCoord = vTexCoord;\n" +
            "    gl_Position = vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
            "}";

    private static FloatBuffer VERTEX_BUF, TEXTURE_COORD_BUF;

    static {
        final float SQUARE_COORDS[] = {
                1.0f, -1.0f,
                -1.0f, -1.0f,
                1.0f, 1.0f,
                -1.0f, 1.0f,
        };

        VERTEX_BUF = ByteBuffer.allocateDirect(SQUARE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        VERTEX_BUF.put(SQUARE_COORDS);
        VERTEX_BUF.position(0);


        final float TEXTURE_COORDS[] = {
                1.0f, 1.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 0.0f,
        };

        TEXTURE_COORD_BUF = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        TEXTURE_COORD_BUF.put(TEXTURE_COORDS);
        TEXTURE_COORD_BUF.position(0);
    }

    private int program;

    BlurFilter(Context context) {
        program = buildProgram(VERTEX_SHADER, getStringFromRaw(context, R.raw.blur));
    }


    void draw(int textureId, int width, int height) {
        // Use shaders
        GLES20.glUseProgram(program);

        int iChannel0Location = GLES20.glGetUniformLocation(program, "iChannel0");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(iChannel0Location, 0);

        int vPositionLocation = GLES20.glGetAttribLocation(program, "vPosition");
        GLES20.glEnableVertexAttribArray(vPositionLocation);
        GLES20.glVertexAttribPointer(vPositionLocation, 2, GLES20.GL_FLOAT, false, 4 * 2, VERTEX_BUF);

        int vTexCoordLocation = GLES20.glGetAttribLocation(program, "vTexCoord");
        GLES20.glEnableVertexAttribArray(vTexCoordLocation);
        GLES20.glVertexAttribPointer(vTexCoordLocation, 2, GLES20.GL_FLOAT, false, 4 * 2, TEXTURE_COORD_BUF);

        int iResolutionLocation = GLES20.glGetUniformLocation(program, "iResolution");
        GLES20.glUniform3fv(iResolutionLocation, 1,
                FloatBuffer.wrap(new float[]{(float) width, (float) height, 1.0f}));

        // Render to screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private static int buildProgram(String vertexSource, String fragmentSource) {
        final int vertexShader = buildShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }

        final int fragmentShader = buildShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            return 0;
        }

        final int program = GLES20.glCreateProgram();
        if (program == 0) {
            return 0;
        }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        return program;
    }

    private static int buildShader(int type, String shaderSource) {
        final int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        GLES20.glShaderSource(shader, shaderSource);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e("BlurFilter", GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    private static String getStringFromRaw(Context context, int id) {
        String str;
        try {
            Resources r = context.getResources();
            InputStream is = r.openRawResource(id);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = is.read();
            while (i != -1) {
                baos.write(i);
                i = is.read();
            }

            str = baos.toString();
            is.close();
        } catch (IOException e) {
            str = "";
        }

        return str;
    }
}
