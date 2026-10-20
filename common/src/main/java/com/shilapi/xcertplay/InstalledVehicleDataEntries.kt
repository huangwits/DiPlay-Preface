package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.profile.VehicleProfile
import com.shilapi.xcertplay.vehicle.source.VehicleDataEntry
import com.geelyplay.preface.vehicle.E01VehicleDataEntry
import com.geelyplay.preface.vehicle.E01VehicleDataEntry.Companion.supported
import android.content.Context

/**
 * Every vehicle data entry this app ships, in settings order. A backend for another car is one
 * more [VehicleDataEntry] in this list, next to the JSON profile; settings offers a choice once
 * there is more than one, and CarPlay reads whichever entry is selected.
 */
internal object InstalledVehicleDataEntries {
    fun all(context: Context, jsonProfile: () -> VehicleProfile): List<VehicleDataEntry> = buildList {
        if (supported()) add(E01VehicleDataEntry())
        if (runCatching { Class.forName("android.car.Car", false, context.classLoader) }.isSuccess) {
            add(JsonProfileVehicleDataEntry(jsonProfile))
        }
    }
}
