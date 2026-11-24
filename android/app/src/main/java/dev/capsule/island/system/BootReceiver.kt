package dev.capsule.island.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The app process starting is enough: CapsuleApp syncs the hosts once settings load. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
