# Roomio - Hotel Booking App

## Firebase Integration

This Android app now integrates with Firebase Firestore to load data for the "Hot Reviews" and "Deals" sections.

### Features Implemented

1. **Firebase Data Models**
   - `Hotel.kt` - Complete hotel information and metadata
   - `User.kt` - User profiles and ratings
   - `Deal.kt` - Hotel deals and promotional offers (updated from tour packages)
   - `HotReview.kt` - Hot review data for the home screen

2. **Enhanced Firebase Repository**
   - `FirebaseRepository.kt` - Comprehensive Firebase operations
   - Methods to fetch active hotel deals and hot reviews
   - Advanced querying by location, price range, and rating
   - Hotel-specific operations and data management
   - Error handling with fallback to sample data

3. **Comprehensive Data Seeding**
   - `FirebaseDataSeeder.kt` - Utility to populate Firebase with sample data
   - Includes complete hotel information, deals, and reviews
   - Automatic data clearing before seeding
   - Realistic Vietnamese hotel data with proper relationships

4. **UI Integration**
   - Updated `HomeFragment.kt` to load data from Firebase
   - Image loading with Glide for both local and remote images
   - Real-time data updates with coroutines
   - Data management buttons (Seed/Clear) for testing
   - Fallback to sample data if Firebase is unavailable

### Database Structure

Based on the SQL Server database schema, the Firebase collections are:

#### `hotels` Collection
```json
{
  "hotelId": "string",
  "ownerId": "string",
  "hotelName": "string",
  "hotelAddress": "string",
  "hotelFloors": "number",
  "hotelTotalRooms": "number",
  "typeId": "string",
  "description": "string",
  "statusId": "string",
  "createdAt": "timestamp",
  "images": "array of strings (URLs)",
  "averageRating": "number (1-5)",
  "totalReviews": "number",
  "pricePerNight": "number"
}
```

#### `hot_reviews` Collection
```json
{
  "hotelId": "string",
  "hotelName": "string", 
  "hotelImage": "string (URL)",
  "rating": "number (1-5)",
  "totalReviews": "number",
  "pricePerNight": "number",
  "location": "string",
  "isHot": "boolean",
  "createdAt": "timestamp"
}
```

#### `deals` Collection
```json
{
  "hotelName": "string",
  "hotelLocation": "string", 
  "description": "string",
  "imageUrl": "string (URL)",
  "originalPricePerNight": "number",
  "discountPricePerNight": "number",
  "discountPercentage": "number",
  "validFrom": "timestamp",
  "validTo": "timestamp",
  "isActive": "boolean",
  "hotelId": "string",
  "roomType": "string",
  "amenities": "array of strings",
  "rating": "number (1-5)",
  "totalReviews": "number",
  "createdAt": "timestamp"
}
```

### How to Use

1. **Setup Firebase**
   - Ensure `google-services.json` is in the `app/` directory
   - Firebase project should have Firestore enabled

2. **Seed Sample Data**
   - Run the app
   - Tap the "Seed Firebase Data" button on the home screen
   - This will clear existing data and populate Firebase with complete hotel information, deals, and reviews

3. **Clear Data (Testing)**
   - Tap the "Clear Data" button to remove all Firebase data
   - Useful for testing and resetting the database

4. **View Data**
   - Hot Reviews section will show hotels with high ratings
   - Deals section will show active hotel promotional offers
   - Data loads automatically from Firebase

### Dependencies Added

- `kotlinx-coroutines-android` - For async operations
- `kotlinx-coroutines-core` - Core coroutines support
- `firebase-firestore` - Firebase Firestore database
- `glide` - Image loading library

### Error Handling

- If Firebase is unavailable, the app falls back to sample data
- Network errors are handled gracefully with user notifications
- Image loading failures show placeholder images

### Future Enhancements

- Real-time updates when data changes in Firebase
- User authentication and personalized content
- Offline data caching
- Push notifications for new deals
- User reviews and ratings system
