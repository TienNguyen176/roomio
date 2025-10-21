# Firebase Database Setup Guide

## Quick Setup Steps

### 1. **Create Firebase Project**

1. Go to https://console.firebase.google.com
2. Click "Create a project"
3. Project name: `roomio-2e37f`
4. Enable Google Analytics (optional)
5. Click "Create project"

### 2. **Enable Firestore Database**

1. In Firebase Console, click "Firestore Database"
2. Click "Create database"
3. Choose "Start in test mode" (allows read/write for 30 days)
4. Select a location (choose closest to your region)
5. Click "Done"

### 3. **Add Android App**

1. In Firebase Console, click the Android icon
2. Android package name: `com.tdc.nhom6.roomio`
3. App nickname: `Roomio Android`
4. SHA-1: Leave blank for now
5. Click "Register app"

### 4. **Download Configuration File**

1. Download `google-services.json`
2. Place it in `app/` directory (same level as `build.gradle.kts`)
3. Click "Next" through remaining steps

### 5. **Update Firestore Rules**

1. Go to Firestore Database → Rules tab
2. Replace existing rules with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow read/write access to all documents for testing
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

3. Click "Publish"

### 6. **Test the Setup**

1. Run your Android app
2. Tap "Test Connection" button
3. Should show: "✅ Firebase connected! Collections: {}"
4. Tap "Seed Firebase Data" button
5. Should show: "Data seeded successfully!"
6. App should now load hotel data

## Troubleshooting

### If "Test Connection" Fails:

1. **Check Internet Connection**
   - Ensure device has internet access
   - Try WiFi and mobile data

2. **Verify google-services.json**
   - File should be in `app/` directory
   - Package name should match: `com.tdc.nhom6.roomio`

3. **Check Firebase Project**
   - Ensure project is active in Firebase Console
   - Verify Firestore Database is enabled

### If Connection Works But No Data:

1. **Seed Data Manually**
   - Tap "Seed Firebase Data" button
   - Wait for success message

2. **Check Firestore Rules**
   - Ensure rules allow read/write access
   - Rules should be published

3. **Verify Collections**
   - Go to Firebase Console → Firestore Database
   - Should see collections: `deals`, `hot_reviews`, `hotels`

## Expected Results

After successful setup:
- **Test Connection**: "✅ Firebase connected! Collections: {deals=6, hot_reviews=5, hotels=6}"
- **Hot Reviews**: Shows 5 hotels with ratings
- **Deals**: Shows 6 hotel deals with discounts
- **Sample Data**: Falls back if Firebase fails

## File Structure

```
app/
├── google-services.json  ← Firebase config file
├── build.gradle.kts
└── src/main/java/com/tdc/nhom6/roomio/
    ├── model/
    ├── repository/
    ├── utils/
    └── ui/
```

## Firebase Console URL

Your Firebase project: https://console.firebase.google.com/project/roomio-2e37f

