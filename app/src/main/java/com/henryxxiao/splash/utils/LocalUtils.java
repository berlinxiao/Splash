package com.henryxxiao.splash.utils;

import android.app.Activity;
import android.content.res.Configuration;

import java.util.Locale;

/***
 * 设置语言环境
 * activity
 * locale 比如，Locale.ENGLISH
 */
public class LocalUtils {
    public static void setLanguage(Activity activity, Locale locale) {
        if (activity == null) {
            throw new NullPointerException("activity cannot be null");
        }
        //Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration();
        configuration.setLocale(locale);
        activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
    }
}
