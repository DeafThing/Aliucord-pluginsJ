/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package io.github.juby210.acplugins;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.aliucord.patcher.InsteadHook;
import com.aliucord.widgets.LinearLayout;
import com.discord.app.AppBottomSheet;
import com.discord.utilities.color.ColorCompat;
import com.discord.views.CheckedSetting;
import com.discord.widgets.media.WidgetMedia;
import com.lytefast.flexinput.R;

import java.util.regex.Pattern;

@AliucordPlugin
@SuppressWarnings("unused")
public final class RemoveZoomLimit extends Plugin {
    private static final long MAX_CANVAS_PIXELS = 100_000_000L;
    
    public RemoveZoomLimit() {
        settingsTab = new SettingsTab(PluginSettings.class, SettingsTab.Type.BOTTOM_SHEET).withArgs(this);
    }

    public static class PluginSettings extends AppBottomSheet {
        public int getContentViewResId() { return 0; }

        private final RemoveZoomLimit plugin;
        public PluginSettings(RemoveZoomLimit plugin) {
            this.plugin = plugin;
        }

        @Nullable
        @Override
        @SuppressLint("SetTextI18n")
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            var context = inflater.getContext();
            var layout = new LinearLayout(context);
            layout.setBackgroundColor(ColorCompat.getThemedColor(context, R.b.colorBackgroundPrimary));

            var cs = Utils.createCheckedSetting(context, CheckedSetting.ViewType.SWITCH, "Disable max resolution limit", "Warning: It can cause crashes on some devices with very large images");
            cs.setChecked(plugin.settings.getBool("removeMaxRes", false));
            cs.setOnCheckedListener(c -> {
                plugin.settings.setBool("removeMaxRes", c);
                plugin.removeMaxRes();
            });
            layout.addView(cs);

            return layout;
        }
    }

    @Override
    public void start(Context context) throws Throwable {
        var pattern = Pattern.compile("width=\\d+&height=\\d+");
        patcher.patch(WidgetMedia.class, "getFormattedUrl", new Class<?>[]{ Context.class, Uri.class }, new Hook(param -> {
            var res = (String) param.getResult();
            if (res.contains(".discordapp.net/")) {
                String newUrl = pattern.matcher(res).replaceFirst("");
                if (!settings.getBool("removeMaxRes", false)) {
                    if (newUrl.contains("?")) {
                        newUrl += "&width=8192&height=8192";
                    } else {
                        newUrl += "?width=8192&height=8192";
                    }
                }
                
                param.setResult(newUrl);
            }
        }));
        patchZoomController();
        
        removeMaxRes();
    }

    private void patchZoomController() throws Throwable {
        var zoomControllerClass = b.f.l.b.c.class;
        var limitScaleMethod = zoomControllerClass.getDeclaredMethod("f", Matrix.class, float.class, float.class, int.class);
        
        patcher.patch(limitScaleMethod, new InsteadHook(param -> {
            Matrix matrix = (Matrix) param.args[0];
            float scale = (Float) param.args[1];
            float focusX = (Float) param.args[2];
            float focusY = (Float) param.args[3];
            float[] values = new float[9];
            matrix.getValues(values);
            float currentScaleX = values[Matrix.MSCALE_X];
            float currentScaleY = values[Matrix.MSCALE_Y];
            float newScaleX = currentScaleX * scale;
            float newScaleY = currentScaleY * scale;
            float baseWidth = 2048f;
            float baseHeight = 2048f;
            try {
                long estimatedCanvasSize = (long)(baseWidth * Math.abs(newScaleX) * baseHeight * Math.abs(newScaleY) * 4);
                if (estimatedCanvasSize > 150_000_000L) {
                    logger.debug("Preventing zoom that would exceed canvas size limit");
                    return true;
                }
            } catch (Exception e) {
                if (Math.abs(newScaleX) > 10f || Math.abs(newScaleY) > 10f) {
                    return true;
                }
            }
            
            return false;
        }));
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }

    private Runnable maxResUnpatch;

    public void removeMaxRes() {
        if (settings.getBool("removeMaxRes", false)) {
            var f = b.f.j.d.f.class;
            var e = b.f.j.d.e.class;
            var i = int.class;
            
            for (var m : b.c.a.a0.d.class.getDeclaredMethods()) {
                var params = m.getParameterTypes();
                if (params.length == 4 && params[0] == f && params[1] == e && params[3] == i) {
                    logger.debug("Found obfuscated method to limit resolution: " + m.getName());
                    maxResUnpatch = patcher.patch(m, new InsteadHook(param -> {
                        return 4;
                    }));
                    break;
                }
            }
        } else if (maxResUnpatch != null) {
            maxResUnpatch.run();
            maxResUnpatch = null;
        }
    }
}