package io.github.juby210.acplugins.messagelogger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import com.aliucord.Utils;
import com.google.android.material.snackbar.Snackbar;

public class RestartUtils {
    public static void promptRestart(Context context, String message) {
        if (!(context instanceof Activity)) {
            Utils.showToast("Cannot restart: context is not an activity");
            return;
        }
        Activity activity = (Activity) context;
        View rootView = activity.findViewById(android.R.id.content);
        Snackbar.make(rootView, message, Snackbar.LENGTH_INDEFINITE)
            .setAction("Restart", v -> {
                try {
                    Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                    if (launchIntent != null) {
                        Intent restartIntent = Intent.makeRestartActivityTask(launchIntent.getComponent());
                        activity.startActivity(restartIntent);
                        Runtime.getRuntime().exit(0);
                    } else {
                        Utils.showToast("Failed to restart the app");
                    }
                } catch (Exception e) {
                    Utils.showToast("Error restarting: " + e.getMessage());
                }
            }).show();
    }
}
