package com.tsfakr.carspeedlimit

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.database.*

class SpeedMonitoringWorker(private val context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams), LocationListener {

    private lateinit var database: DatabaseReference
    private lateinit var locationManager: LocationManager
    private var maxSpeedLimit: Float = 0f // Default speed limit

    override fun doWork(): Result {
        database = FirebaseDatabase.getInstance().reference.child("speed_limits")
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        fetchSpeedLimit()
        requestLocationUpdates()

        return Result.success()
    }

    private fun fetchSpeedLimit() {
        val userId = "renter_tsfakr" // This should be dynamically fetched based on the authenticated user
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                maxSpeedLimit = snapshot.getValue(Float::class.java) ?: 0f
                Log.d("SpeedMonitorWorker", "Updated Speed Limit: $maxSpeedLimit km/h")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SpeedMonitorWorker", "Failed to read speed limit", error.toException())
            }
        })
    }

    private fun requestLocationUpdates() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null)
        }
    }

    override fun onLocationChanged(location: Location) {
        val speedKmh = location.speed * 3.6f // Convert m/s to km/h
        Log.d("SpeedMonitorWorker", "Current Speed: $speedKmh km/h")

        if (speedKmh > maxSpeedLimit) {
            notifySpeedViolation(speedKmh)
        }
    }

    private fun notifySpeedViolation(speed: Float) {
        sendFirebaseNotification(speed)
        showUserWarning(speed)
    }

    private fun sendFirebaseNotification(speed: Float) {
        val notificationData = mapOf("speed" to speed, "message" to "Speed limit exceeded!")
        FirebaseDatabase.getInstance().reference.child("alerts").push().setValue(notificationData)
    }
/*
    // firebase data json
    {
        "speed_limits": {
        "renter_123": 80,
        "renter_456": 90
    },
        "alerts": {
        "alert_1": { "speed": 85, "message": "Speed limit exceeded!" }
    }
    }
*/

/*
    // If using AWS then we can implement below code
    fun sendAwsSnsAlert(speed: Float) {
        val client = AmazonSNSClient(AWSCredentials()) // Replace with actual credentials
        val message = "Speed limit exceeded! Current Speed: $speed km/h"

        val request = PublishRequest()
            .withMessage(message)
            .withPhoneNumber("+91 9709080680") // Replace with the fleet manager's phone number

        client.publish(request)
    }
*/


    private fun showUserWarning(speed: Float) {
        val channelId = "speed_warning_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Speed Warnings",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Speed Warning!")
            .setContentText("You are driving at $speed km/h. Reduce your speed.")
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(2, notification)
    }
}
