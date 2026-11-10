package com.example.progressoverlay;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class IconPalette {
    final int[] pair, abundant, horizontal, vertical, ring;
    private static final Map<String, IconPalette> CACHE = new HashMap<>();

    private IconPalette(int[] pair, int[] abundant, int[] horizontal, int[] vertical, int[] ring) {
        this.pair=pair; this.abundant=abundant; this.horizontal=horizontal;
        this.vertical=vertical; this.ring=ring;
    }
    static IconPalette sample() {
        return fromPair(new int[]{OverlayController.DEFAULT_COLOR, OverlayController.DEFAULT_COLOR});
    }
    private static IconPalette fromPair(int[] pair) {
        return new IconPalette(pair,pair,pair,pair,pair);
    }
    int[] stops(int mode, boolean circular, boolean verticalBar) {
        if (mode==1) return abundant;
        if (mode==2) return circular?ring:verticalBar?vertical:horizontal;
        return pair;
    }
    static IconPalette forPackage(Context c, String pkg) {
        IconPalette cached=CACHE.get(pkg); if(cached!=null) return cached;
        try {
            IconPalette palette=extract(c.getPackageManager().getApplicationIcon(pkg));
            if(palette!=null) CACHE.put(pkg,palette);
            return palette;
        } catch(PackageManager.NameNotFoundException | RuntimeException ignored) { return null; }
    }
    static IconPalette forNotification(Context c, StatusBarNotification sbn) {
        IconPalette palette=forPackage(c,sbn.getPackageName());
        if(palette==null) try {
            Icon icon=sbn.getNotification().getSmallIcon();
            if(icon!=null) palette=extract(icon.loadDrawable(c));
        } catch(RuntimeException ignored) { }
        if(palette==null && sbn.getNotification().color!=Notification.COLOR_DEFAULT) {
            int color=sbn.getNotification().color|0xff000000;
            palette=fromPair(new int[]{color, shade(color)});
        }
        return palette;
    }
    private static IconPalette extract(Drawable drawable) {
        Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        drawable=drawable.mutate(); drawable.setBounds(0,0,64,64);
        drawable.draw(new Canvas(bitmap));
        int[] pixels=new int[4096]; bitmap.getPixels(pixels,0,64,0,0,64,64); bitmap.recycle();
        // Four coarse levels per RGB channel group antialiasing and nearby shades.
        long[][] bins=new long[64][4], columns=new long[8][4], rows=new long[8][4], sectors=new long[12][4];
        int total=0;
        for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
            int c=pixels[y*64+x]; if(Color.alpha(c)<128) continue;
            int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            total++;
            add(bins[((r>>6)<<4)|((g>>6)<<2)|(b>>6)],r,g,b);
            add(columns[x/8],r,g,b); add(rows[y/8],r,g,b);
            double angle=Math.atan2(x-31.5,31.5-y);
            int sector=(int)Math.floor(((angle+Math.PI*2)%(Math.PI*2))*12/(Math.PI*2));
            add(sectors[sector],r,g,b);
        }
        if(total==0) return null;
        Integer[] order=new Integer[64]; for(int i=0;i<64;i++) order[i]=i;
        Arrays.sort(order,(a,b)->Long.compare(bins[b][3],bins[a][3]));
        ArrayList<long[]> groups=new ArrayList<>();
        for(int key:order) {
            long[] bin=bins[key]; if(bin[3]==0) break;
            long[] match=null;
            for(long[] group:groups) if(distance(color(bin),color(group))<85) { match=group; break; }
            if(match==null) { match=new long[4]; groups.add(match); }
            for(int i=0;i<4;i++) match[i]+=bin[i];
        }
        groups.sort((a,b)->Long.compare(b[3],a[3]));
        int primary=color(groups.get(0)), secondary=shade(primary);
        for(int i=1;i<groups.size();i++) if(distance(primary,color(groups.get(i)))>=85) {
            secondary=color(groups.get(i)); break;
        }
        ArrayList<Integer> visible=new ArrayList<>();
        for(long[] group:groups) if(group[3]*10>total) visible.add(color(group));
        if(visible.isEmpty()) visible.add(primary);
        int[] abundant=new int[visible.size()]; for(int i=0;i<abundant.length;i++) abundant[i]=visible.get(i);
        return new IconPalette(new int[]{primary,secondary},abundant,
                colors(columns,primary),colors(rows,primary),colors(sectors,primary));
    }
    private static void add(long[] group,int r,int g,int b) {
        group[0]+=r; group[1]+=g; group[2]+=b; group[3]++;
    }
    private static int color(long[] group) {
        return Color.rgb((int)(group[0]/group[3]),(int)(group[1]/group[3]),(int)(group[2]/group[3]));
    }
    private static int[] colors(long[][] groups,int fallback) {
        int[] out=new int[groups.length];
        for(int i=0;i<out.length;i++) out[i]=groups[i][3]==0?fallback:color(groups[i]);
        return out;
    }
    private static double distance(int a,int b) {
        int r=Color.red(a)-Color.red(b),g=Color.green(a)-Color.green(b),bl=Color.blue(a)-Color.blue(b);
        return Math.sqrt(r*r+g*g+bl*bl);
    }
    private static int shade(int color) {
        float[] hsv=new float[3];Color.colorToHSV(color,hsv);
        hsv[2]=hsv[2]>.5f?Math.max(.15f,hsv[2]*.55f):Math.min(1f,hsv[2]+.38f);
        return Color.HSVToColor(hsv);
    }
}
