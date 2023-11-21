package com.simplemobiletools.dialer.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.dialer.models.RecentCall


object SharedPreferencesHelper {

    private const val PREFERENCES_NAME = "MyAppPreferences"
    private const val KEY_RECENT_CALLS = "recent_calls"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun saveRecentCalls(context: Context, recentCalls: ArrayList<RecentCall>) {
        val editor = getSharedPreferences(context).edit()
        val gson = Gson()
        val json = gson.toJson(recentCalls)
        editor.putString(KEY_RECENT_CALLS, json)
        editor.apply()
    }

    fun getRecentCalls(context: Context): ArrayList<RecentCall> {
        val gson = Gson()
        val json = getSharedPreferences(context).getString(KEY_RECENT_CALLS, "")
        val type = object : TypeToken<ArrayList<RecentCall>>() {}.type
        return gson.fromJson(json, type) ?: ArrayList()
    }
}
