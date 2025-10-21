# Firebase Database Loading Troubleshooting Guide

## Common Issues and Solutions

### 1. **Firebase Connection Issues**

**Symptoms:**
- App shows "Firebase connection failed!" message
- No data loads from Firebase
- Falls back to sample data

**Solutions:**
1. **Check Internet Connection**
   - Ensure device has internet access
   - Test with WiFi and mobile data

2. **Verify Firebase Configuration**
   - Ensure `google-services.json` is in `app/` directory
   - Check that package name matches: `com.tdc.nhom6.roomio`
   - Verify Firebase project is active

3. **Test Firebase Connection**
   - Tap "Test Connection" button in the app
   - Check console logs for detailed error messages

### 2. **Firestore Rules Issues**

**Symptoms:**
- Connection test passes but data doesn't load
- Permission denied errors in logs

**Solutions:**
1. **Update Firestore Rules**
   - Go to Firebase Console → Firestore Database → Rules
   - Use the provided `firestore.rules` file for testing:
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if true;
       }
     }
   }
   ```

2. **Publish Rules**
   - Click "Publish" in Firebase Console after updating rules

### 3. **Empty Database**

**Symptoms:**
- Connection works but shows "No data found in Firebase"
- Collections exist but are empty

**Solutions:**
1. **Seed Data**
   - Tap "Seed Firebase Data" button
   - This will populate the database with sample hotels and deals

2. **Check Collection Counts**
   - Tap "Test Connection" to see collection counts
   - Should show: deals: 6, hot_reviews: 5, hotels: 6

### 4. **Data Loading Issues**

**Symptoms:**
- Data loads but UI doesn't update
- Empty lists in Hot Reviews or Deals sections

**Solutions:**
1. **Check Data Structure**
   - Ensure Firebase data matches the model structure
   - Verify field names and types match exactly

2. **Clear and Reseed**
   - Tap "Clear Data" to remove all Firebase data
   - Tap "Seed Firebase Data" to repopulate

### 5. **Debugging Steps**

**Step 1: Test Connection**
```
1. Open app
2. Tap "Test Connection" button
3. Check toast message for connection status
4. Look at console logs for detailed information
```

**Step 2: Check Collections**
```
1. Go to Firebase Console → Firestore Database
2. Verify these collections exist:
   - deals (should have 6 documents)
   - hot_reviews (should have 5 documents)  
   - hotels (should have 6 documents)
```

**Step 3: Verify Data Structure**
```
Check that deals collection has these fields:
- hotelName: string
- hotelLocation: string
- isActive: boolean
- discountPricePerNight: number
- rating: number
```

**Step 4: Check Logs**
```
Look for these log messages:
- "FirebaseRepository: Testing Firebase connection..."
- "FirebaseRepository: Found X active deals"
- "FirebaseRepository: Found X hot reviews"
```

### 6. **Emergency Fallback**

If Firebase continues to fail:
1. The app will automatically fall back to sample data
2. Hot Reviews and Deals sections will show local sample data
3. App functionality remains intact

### 7. **Firebase Console Setup**

**Required Steps:**
1. **Create Firebase Project**
   - Go to https://console.firebase.google.com
   - Create new project: "roomio-2e37f"

2. **Enable Firestore**
   - Go to Firestore Database
   - Click "Create database"
   - Choose "Start in test mode"

3. **Add Android App**
   - Go to Project Settings → General
   - Add Android app with package: `com.tdc.nhom6.roomio`
   - Download `google-services.json`

4. **Update Rules**
   - Go to Firestore Database → Rules
   - Use the provided rules for testing

### 8. **Testing Checklist**

- [ ] Internet connection working
- [ ] Firebase project active
- [ ] `google-services.json` in correct location
- [ ] Firestore rules allow read/write
- [ ] Collections populated with data
- [ ] App shows "Data loaded successfully!" message

### 9. **Contact Support**

If issues persist:
1. Check Android Studio console for detailed error logs
2. Verify Firebase project configuration
3. Test with different devices/emulators
4. Ensure Firebase project billing is enabled (if required)

