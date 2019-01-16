/*
 * Copyright (C) 2011 Keijiro Takahashi
 * Copyright (C) 2012 GREE, Inc.
 * 
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package net.gree.unitywebview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.FrameLayout;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import com.unity3d.player.UnityPlayer;
import android.graphics.Canvas;
import android.util.AttributeSet;

import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.io.ByteArrayOutputStream;

class CWebViewPluginInterface {
    private CWebViewPlugin mPlugin;
    private String mGameObject;

    public CWebViewPluginInterface(CWebViewPlugin plugin, String gameObject) {
        mPlugin = plugin;
        mGameObject = gameObject;
    }

    @JavascriptInterface
    public void call(final String message) {
        call("CallFromJS", message);
    }

    public void call(final String method, final String message) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mPlugin.IsInitialized()) {
                UnityPlayer.UnitySendMessage(mGameObject, method, message);
            }
        }});
    }
}
class CustomRenderer implements GLSurfaceView.Renderer {
    private SurfaceTexture surfaceTexture = null;
    public int glSurfaceTex;
    public Surface surface = null;
    @Override
    public void onDrawFrame( GL10 gl ) {
        synchronized ( this ) {
            surfaceTexture.updateTexImage(); // Update texture
        }
    }
    public int getglSurfaceTex(){
        return  glSurfaceTex;
    }

    @Override
    public void onSurfaceChanged( GL10 gl, int width, int height ) {
        surface = null;
        surfaceTexture = null;
        glSurfaceTex = Engine_CreateSurfaceTexture( width, height );
        if ( glSurfaceTex > 0 ) {
            surfaceTexture = new SurfaceTexture( glSurfaceTex );
            surfaceTexture.setDefaultBufferSize( width, height );
            surface = new Surface( surfaceTexture );
        }
    }
    @Override
    public void onSurfaceCreated( GL10 gl, EGLConfig config ) {
        final String extensions = GLES20.glGetString( GLES20.GL_EXTENSIONS );
    }
    int Engine_CreateSurfaceTexture( int width, int height ) {
        int[] glTexture = new int[1];
        GLES20.glGenTextures( 1, glTexture,0 );

        GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, glTexture[0] );
        // Notice the use of GL_TEXTURE_2D for texture creation
        GLES20.glTexImage2D( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE );
        GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE );
        GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST );
        GLES20.glTexParameteri( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST );
        GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );

        return glTexture[0];
    }
}

class CustomWebView extends WebView   {

    private int scrollX = 0;
    private int scrollY = 0;
    public int textureId;
    public float scale=0;
    public Surface surface = null;
    public int websiteWidth = 1280;
    public int websiteHeight = 1280;
    public boolean isBufferFinished=false;
    Bitmap bitmap;
    Canvas canvas;
    ByteArrayOutputStream stream;
    public byte[] Buffer;

    public CustomWebView(Context context, Surface ss) {
        super(context);
        bitmap = Bitmap.createBitmap(websiteWidth, websiteHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        stream = new ByteArrayOutputStream();
    /*    surface=ss;
        if(surface==null){
            glSurfaceTex = Engine_CreateSurfaceTexture( websiteWidth, websiteHeight );
            if ( glSurfaceTex > 0 ) {
                surfaceTexture = new SurfaceTexture( glSurfaceTex );
                surfaceTexture.setDefaultBufferSize( websiteWidth, websiteHeight );
                surface = new Surface( surfaceTexture );
            }
        }
        */
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        scrollX = l;
        scrollY = t;
    }

    @Override
    public void draw(Canvas originCanvas) {
        if (surface != null) {
            canvas = surface.lockCanvas(null);
            //final Canvas canvas = surface.lockCanvas(null); // Android canvas from surface

            super.onDraw(canvas); // Call the WebView onDraw targetting the canvas

            surface.unlockCanvasAndPost(canvas); // We're done with the canvas!
            Buffer = stream.toByteArray();
            isBufferFinished=true;
            stream.reset();

        }
    }
}

public class CWebViewPlugin   {
    private static FrameLayout layout = null;
    private CustomWebView mWebView;
    private CWebViewPluginInterface mWebViewPlugin;
    private int progress;
    private boolean canGoBack;
    private boolean canGoForward;
    private Hashtable<String, String> mCustomHeaders;
    private String mWebViewUA;
    public Surface surface         = null;
    CustomRenderer renderer;
    GLSurfaceView glSurfaceView;

    public CWebViewPlugin() {
    }

    public boolean IsInitialized() {
        return mWebView != null;
    }
    public void Init(final String gameObject, final boolean transparent, final String ua) {
        final CWebViewPlugin self = this;
        final Activity a = UnityPlayer.currentActivity;




        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView != null) {
                return;
            }
            mCustomHeaders = new Hashtable<String, String>();
            renderer = new CustomRenderer();
            final CustomWebView webView = new CustomWebView(a, surface);
            webView.setVisibility(View.GONE);
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);

            // webView.setWebChromeClient(new WebChromeClient() {
            //     public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
            //         Log.d("Webview", cm.message());
            //         return true;
            //     }
            // });
            webView.setWebChromeClient(new WebChromeClient() {
                View videoView;

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    progress = newProgress;
                }
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    super.onShowCustomView(view, callback);
                    if (layout != null) {
                        videoView = view;
                        layout.setBackgroundColor(0xff000000);
                        layout.addView(videoView);
                    }
                }

                @Override
                public void onHideCustomView() {
                    super.onHideCustomView();
                    if (layout != null) {
                        layout.removeView(videoView);
                        layout.setBackgroundColor(0x00000000);
                        videoView = null;
                    }
                }
            });

            mWebViewPlugin = new CWebViewPluginInterface(self, gameObject);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    webView.loadUrl("about:blank");
                    canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                    mWebViewPlugin.call("CallOnError", errorCode + "\t" + description + "\t" + failingUrl);
                }
                
                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                	canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                    mWebViewPlugin.call("CallOnHttpError", Integer.toString(errorResponse.getStatusCode()));
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                    mWebViewPlugin.call("CallOnStarted", url);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                    mWebViewPlugin.call("CallOnLoaded", url);
                }

                @Override
                public void onLoadResource(WebView view, String url) {
                    canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    if (mCustomHeaders == null || mCustomHeaders.isEmpty()) {
                        return super.shouldInterceptRequest(view, url);
                    }

                    try {
                        HttpURLConnection urlCon = (HttpURLConnection) (new URL(url)).openConnection();
                        // The following should make HttpURLConnection have a same user-agent of webView)
                        // cf. http://d.hatena.ne.jp/faw/20070903/1188796959 (in Japanese)
                        urlCon.setRequestProperty("User-Agent", mWebViewUA);

                        for (HashMap.Entry<String, String> entry: mCustomHeaders.entrySet()) {
                            urlCon.setRequestProperty(entry.getKey(), entry.getValue());
                        }

                        urlCon.connect();

                        return new WebResourceResponse(
                            urlCon.getContentType().split(";", 2)[0],
                            urlCon.getContentEncoding(),
                            urlCon.getInputStream()
                        );

                    } catch (Exception e) {
                        return super.shouldInterceptRequest(view, url);
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    canGoBack = webView.canGoBack();
                    canGoForward = webView.canGoForward();
                    if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("file://") || url.startsWith("javascript:")) {
                        // Let webview handle the URL
                        return false;
                    } else if (url.startsWith("unity:")) {
                        String message = url.substring(6);
                        mWebViewPlugin.call("CallFromJS", message);
                        return true;
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    PackageManager pm = a.getPackageManager();
                    List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
                    if (apps.size() > 0) {
                        view.getContext().startActivity(intent);
                    }
                    return true;
                }
            });
            webView.addJavascriptInterface(mWebViewPlugin , "Unity");

            WebSettings webSettings = webView.getSettings();
            if (ua != null && ua.length() > 0) {
                webSettings.setUserAgentString(ua);
            }
            mWebViewUA = webSettings.getUserAgentString();
            webSettings.setSupportZoom(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                // Log.i("CWebViewPlugin", "Build.VERSION.SDK_INT = " + Build.VERSION.SDK_INT);
                webSettings.setAllowUniversalAccessFromFileURLs(true);
            }
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            webSettings.setDatabaseEnabled(true);
            webSettings.setDomStorageEnabled(true);
            String databasePath = webView.getContext().getDir("databases", Context.MODE_PRIVATE).getPath();
            webSettings.setDatabasePath(databasePath);

            if (transparent) {
                webView.setBackgroundColor(0x00000000);
            }

            if (layout == null) {
                layout = new FrameLayout(a);
                a.addContentView(
                    layout,
                    new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                layout.setFocusable(true);
                layout.setFocusableInTouchMode(true);
            }
            layout.addView(
                webView,
                new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                    Gravity.NO_GRAVITY));
            mWebView = webView;
            renderer.surface=webView.surface;
        }});

    }
    public byte[] getBuffer(){
        if(mWebView.isBufferFinished){
            mWebView.isBufferFinished=false;
            return  mWebView.Buffer;
        }else{
          return null;
        }
    }
    public int getCurrTexId(){
        return renderer.getglSurfaceTex();
    }
    public void Destroy() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.stopLoading();
            layout.removeView(mWebView);
            mWebView.destroy();
            mWebView = null;
        }});
    }

    public void LoadURL(final String url) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (mCustomHeaders != null && !mCustomHeaders.isEmpty()) {
                mWebView.loadUrl(url, mCustomHeaders);
            } else {
                mWebView.loadUrl(url);;
            }
        }});
    }

    public void LoadHTML(final String html, final String baseURL)
    {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.loadDataWithBaseURL(baseURL, html, "text/html", "UTF8", null);
        }});
    }

    public void EvaluateJS(final String js) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mWebView.evaluateJavascript(js, null);
            } else {
                mWebView.loadUrl("javascript:" + URLEncoder.encode(js));
            }
        }});
    }

    public void GoBack() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.goBack();
        }});
    }

    public void GoForward() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.goForward();
        }});
    }
    public void SetSize(int  ww, int wh){
        if (mWebView == null) {
            return;
        }

    }
    public void SetMargins(int left, int top, int right, int bottom) {
        final FrameLayout.LayoutParams params
            = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.NO_GRAVITY);
        params.setMargins(left, top, right, bottom);
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.setLayoutParams(params);
        }});
    }

    public void SetVisibility(final boolean visibility) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (visibility) {
                mWebView.setVisibility(View.VISIBLE);
                layout.requestFocus();
                mWebView.requestFocus();
            } else {
                mWebView.setVisibility(View.GONE);
            }
        }});
    }

    // cf. https://stackoverflow.com/questions/31788748/webview-youtube-videos-playing-in-background-on-rotation-and-minimise/31789193#31789193
    public void OnApplicationPause(final boolean paused) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (paused) {
                mWebView.onPause();
                mWebView.pauseTimers();
            } else {
                mWebView.onResume();
                mWebView.resumeTimers();
            }
        }});
    }

    public void AddCustomHeader(final String headerKey, final String headerValue)
    {
        if (mCustomHeaders == null) {
            return;
        }
        mCustomHeaders.put(headerKey, headerValue);
    }

    public String GetCustomHeaderValue(final String headerKey)
    {
        if (mCustomHeaders == null) {
            return null;
        }

        if (!mCustomHeaders.containsKey(headerKey)) {
            return null;
        }
        return this.mCustomHeaders.get(headerKey);
    }

    public void RemoveCustomHeader(final String headerKey)
    {
        if (mCustomHeaders == null) {
            return;
        }

        if (this.mCustomHeaders.containsKey(headerKey)) {
            this.mCustomHeaders.remove(headerKey);
        }
    }

    public void ClearCustomHeader()
    {
        if (mCustomHeaders == null) {
            return;
        }

        this.mCustomHeaders.clear();
    }

    public void ClearCookies()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) 
        {
           CookieManager.getInstance().removeAllCookies(null);
           CookieManager.getInstance().flush();
        } else {
           final Activity a = UnityPlayer.currentActivity;
           CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(a);
           cookieSyncManager.startSync();
           CookieManager cookieManager = CookieManager.getInstance();
           cookieManager.removeAllCookie();
           cookieManager.removeSessionCookie();
           cookieSyncManager.stopSync();
           cookieSyncManager.sync();
        }
    }

}
