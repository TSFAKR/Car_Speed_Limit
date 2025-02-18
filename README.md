How It Works
Fleet Company Sets Speed Limit in Firebase (per renter).
WorkManager Triggers Every 15 Minutes and fetches GPS Speed.
If Speed Exceeds Limit:
Sends Firebase Notification to the rental company.
Shows Local Warning Notification to the user.
(Optional) Sends an AWS SNS Alert.
