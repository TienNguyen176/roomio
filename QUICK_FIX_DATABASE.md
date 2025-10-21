# 🔧 Fix: Device Cannot Find Database

## Quick Fix Steps

### **Step 1: Check Firebase Setup**
1. **Open Firebase Console**: https://console.firebase.google.com/project/roomio-2e37f
2. **Verify Firestore Database is enabled**:
   - Go to "Firestore Database" in left menu
   - If not enabled, click "Create database" → "Start in test mode"

### **Step 2: Update Firestore Rules**
1. **Go to Firestore Database → Rules tab**
2. **Replace existing rules with**:
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
3. **Click "Publish"**

### **Step 3: Test in App**
1. **Run your Android app**
2. **Tap "Test Connection" button**
   - ✅ Should show: "Firebase connected! Collections: {}"
   - ❌ If fails: Check internet connection and Firebase setup

### **Step 4: Initialize Database**
1. **Tap "Initialize Database" button**
2. **Wait for messages**:
   - "Initializing database structure..."
   - "✅ Database structure created! Now seeding data..."
   - "✅ Database ready! Reloading data..."

### **Step 5: Verify Data**
1. **Check Firebase Console** → Firestore Database
2. **Should see collections**:
   - `deals` (6 documents)
   - `hot_reviews` (5 documents)
   - `hotels` (6 documents)

## Alternative: Manual Setup

If automatic setup fails:

### **Option 1: Use Sample Data**
- App will automatically fall back to sample data
- Hot Reviews and Deals sections will work with local data

### **Option 2: Manual Database Creation**
1. **Go to Firebase Console** → Firestore Database
2. **Click "Start collection"**
3. **Create these collections**:
   - Collection ID: `deals`
   - Collection ID: `hot_reviews`  
   - Collection ID: `hotels`
4. **Tap "Seed Firebase Data" in app**

## Expected Results

After successful setup:
- **App loads automatically** with hotel data
- **Hot Reviews section** shows 5 hotels
- **Deals section** shows 6 hotel deals
- **Toast message**: "✅ Database loaded! Reviews: 5, Deals: 6"

## Troubleshooting

### **If "Test Connection" Fails:**
- Check internet connection
- Verify `google-services.json` is in `app/` directory
- Ensure Firebase project is active

### **If "Initialize Database" Fails:**
- Check Firestore rules allow read/write
- Verify Firebase project has Firestore enabled
- Try manual collection creation in Firebase Console

### **If Data Doesn't Load:**
- Tap "Seed Firebase Data" button
- Check Firebase Console for collections
- Verify collections have documents

## Emergency Fallback

If all else fails:
1. **App will show sample data automatically**
2. **All features work with local data**
3. **No Firebase required for basic functionality**

The app is designed to work with or without Firebase! 🚀

