package cn.nekocode.blurringview.sample;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author nekocode (nekocode.cn@gmail.com)
 */
public class BlurringTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "BlurringTextureView";
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int DRAW_INTERVAL = 1000 / 60;
    private float downsampleSize = 4;

    private Surface backgroundSurface;
    private int backgroundColor;

    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;
    private EGL10 egl10;

    private RenderThread renderThread;

    public BlurringTextureView(Context context) {
        super(context);
        init();
    }

    public BlurringTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlurringTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        super.setSurfaceTextureListener(this);
    }

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            final int[] locations = new int[2];
            if (isShown() && backgroundSurface != null) {
                Activity a = null;
                Context ctx = getContext();
                while (true) {
                    if (ctx instanceof Activity) {
                        a = (Activity) ctx;
                        break;
                    } else if (ctx instanceof ContextWrapper) {
                        ctx = ((ContextWrapper) ctx).getBaseContext();
                    } else {
                        break;
                    }
                }

                if (a == null) {
                    // Not in a activity
                    return true;
                }

                ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
                decor.getLocationInWindow(locations);
                int x = -locations[0];
                int y = -locations[1];

                getLocationInWindow(locations);
                x += locations[0];
                y += locations[1];

                if (decor.getBackground() instanceof ColorDrawable) {
                    backgroundColor = ((ColorDrawable) decor.getBackground()).getColor();
                } else {
                    backgroundColor = 0xffffffff;
                }

                Canvas backgroundCanvas = backgroundSurface.lockCanvas(null);
                int rc = backgroundCanvas.save();
                backgroundCanvas.scale(1f / downsampleSize, 1f / downsampleSize);
                backgroundCanvas.translate(-x, -y);


                BlurringTextureView.this.setVisibility(View.INVISIBLE);
//                ArrayList<View> topViews = findViewsTopOfThis(decor);
//                ArrayList<Pair<View, Integer>> savedVisibilities = new ArrayList<>();
//                for (View view : topViews) {
//                    savedVisibilities.add(new Pair<>(view, view.getVisibility()));
//                    view.setVisibility(View.INVISIBLE);
//                }

                decor.draw(backgroundCanvas);

//                for (Pair<View, Integer> pair : savedVisibilities) {
//                    pair.first.setVisibility(pair.second);
//                }
                BlurringTextureView.this.setVisibility(View.VISIBLE);


                backgroundCanvas.restoreToCount(rc);
                backgroundSurface.unlockCanvasAndPost(backgroundCanvas);
            }

            return true;
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        super.onDetachedFromWindow();
    }

    @Override
    public final void setSurfaceTextureListener(SurfaceTextureListener listener) {
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (renderThread == null) {
            renderThread = new RenderThread(texture);

        } else {
            if (!renderThread.isInterrupted()) {
                renderThread.interrupt();
                renderThread = new RenderThread(texture);
            }
        }

        renderThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (renderThread != null) {
            renderThread.interrupt();
        }

        return true;
    }


    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private static int genTexture() {
        int[] genBuf = new int[1];
        GLES20.glGenTextures(1, genBuf, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, genBuf[0]);

        // Set texture default draw parameters
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return genBuf[0];
    }

    private class RenderThread extends Thread {
        private SurfaceTexture surfaceTexture;

        RenderThread(SurfaceTexture surfaceTexture) {
            this.surfaceTexture = surfaceTexture;
        }

        @Override
        public void run() {
            initGL(surfaceTexture);

            int textureId = genTexture();
            int width = (int) (getWidth() / downsampleSize);
            int height = (int) (getHeight() / downsampleSize);

            SurfaceTexture backgroundTexture = new SurfaceTexture(textureId);
            backgroundTexture.setDefaultBufferSize(width, height);
            backgroundSurface = new Surface(backgroundTexture);

            BlurFilter filter = new BlurFilter(getContext());

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    GLES20.glClearColor(
                            ((backgroundColor << 16) & 0xff) / 255f,
                            ((backgroundColor << 8) & 0xff) / 255f,
                            (backgroundColor & 0xff) / 255f,
                            1f
                    );

                    backgroundTexture.updateTexImage();
                    filter.draw(textureId, getWidth(), getHeight());

                    // Flush
                    GLES20.glFlush();
                    egl10.eglSwapBuffers(eglDisplay, eglSurface);

                    Thread.sleep(DRAW_INTERVAL);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (backgroundSurface != null) {
                backgroundSurface.release();
                backgroundSurface = null;
            }

            backgroundTexture.release();
        }
    }

    private void initGL(SurfaceTexture texture) {
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = {
                EGL10.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };

        EGLConfig eglConfig = null;
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("eglChooseConfig failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        }
        if (eglConfig == null) {
            throw new RuntimeException("eglConfig not initialized");
        }

        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            int error = egl10.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW");
                return;
            }
            throw new RuntimeException("eglCreateWindowSurface failed " +
                    android.opengl.GLUtils.getEGLErrorString(error));
        }

        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }
    }

    private ArrayList<View> findViewsTopOfThis(ViewGroup root) {
        ArrayList<View> views = new ArrayList<>();
        findViewsTopOfThis(root, views, new boolean[]{false});
        return views;
    }

    private void findViewsTopOfThis(ViewGroup root, ArrayList<View> topViews, boolean[] isSelfFinded) {
        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = root.getChildAt(i);

            if (isSelfFinded[0]) {
                topViews.add(child);

            } else {
                if (child == this) {
                    topViews.add(child);
                    isSelfFinded[0] = true;

                } else if (child instanceof ViewGroup) {
                    findViewsTopOfThis((ViewGroup) child, topViews, isSelfFinded);
                }
            }
        }
    }
}
