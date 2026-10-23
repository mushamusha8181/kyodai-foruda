package com.mushamusha.giantfolder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

public final class Ui {
    private static volatile Typeface cachedTypeface;
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static int withAlpha(int color, int alphaPercent) {
        int alpha = Math.max(0, Math.min(255, Math.round(alphaPercent * 2.55f)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static Typeface appTypeface(Context c) {
        Typeface ready=cachedTypeface;
        if(ready!=null) return ready;
        synchronized (Ui.class) {
            if(cachedTypeface!=null) return cachedTypeface;
            try {
                String[] names = c.getAssets().list("fonts");
                if (names != null) {
                    for (String n : names) {
                        String low = n.toLowerCase(java.util.Locale.ROOT);
                        if (low.equals("riitegakinr.ttf") || low.equals("riitegakinr.otf") || low.contains("riitegaki")) {
                            cachedTypeface=Typeface.createFromAsset(c.getAssets(), "fonts/" + n);
                            return cachedTypeface;
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {}
            cachedTypeface=Typeface.create("sans-serif", Typeface.NORMAL);
            return cachedTypeface;
        }
    }

    /** Decodes user-selected bitmaps near the requested size instead of loading a full-resolution photo. */
    public static Drawable loadSampledDrawable(Context c, Uri uri, int reqWidthPx, int reqHeightPx) {
        if(c==null||uri==null) return null;
        int rw=Math.max(1,reqWidthPx), rh=Math.max(1,reqHeightPx);
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)) {
            if(in==null) return null;
            BitmapFactory.decodeStream(in,null,bounds);
        } catch(Exception e) { return null; }
        int sample=1;
        if(bounds.outWidth>0&&bounds.outHeight>0) {
            while(bounds.outWidth/(sample*2)>=rw && bounds.outHeight/(sample*2)>=rh) sample*=2;
        }
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,sample);
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=c.getContentResolver().openInputStream(uri)) {
            if(in==null) return null;
            Bitmap bitmap=BitmapFactory.decodeStream(in,null,opts);
            return bitmap==null?null:new BitmapDrawable(c.getResources(),bitmap);
        } catch(Exception e) { return null; }
    }

    /** Keeps programmatic controls out of Android 15+ status/navigation bars (targetSdk 35 edge-to-edge). */
    public static void applySafeInsets(View view) {
        if(view==null||Build.VERSION.SDK_INT<35)return;
        final int baseLeft=view.getPaddingLeft(),baseTop=view.getPaddingTop(),baseRight=view.getPaddingRight(),baseBottom=view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(baseLeft+safe.left,baseTop+safe.top,baseRight+safe.right,baseBottom+safe.bottom);
            return insets;
        });
        if(view.isAttachedToWindow()) view.requestApplyInsets();
        else view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){v.removeOnAttachStateChangeListener(this);v.requestApplyInsets();}
            @Override public void onViewDetachedFromWindow(View v){}
        });
    }

    public static void applyFont(View view, Typeface tf) {
        if (view instanceof TextView) ((TextView) view).setTypeface(tf);
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) applyFont(vg.getChildAt(i), tf);
        }
    }
}
