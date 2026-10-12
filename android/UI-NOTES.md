# Moni Coffee Android UI

Native Android UI based on the PNG references in `../Android-UI/Moni coffee`.

Implemented screens: menu/home, explore/search, favorites, product details, cart, dine-in/pickup checkout, order success, order list and transaction summary, profile, personal information, payment selection, login, registration, verification, and cafe introduction.

Local interactions include category/search filtering, favorite selection, item sizes and multiple add-ons, quantity controls, cart totals, empty states, clear-cart/logout confirmation, required table/pickup input, and locally created orders. Profile changes persist in SharedPreferences. Cart, orders, and favorites are held in memory for this UI prototype.

Authentication accepts any six-digit code for demonstration; no SMS is sent. Payment selection does not process a payment. Menu data and orders are not connected to the Django API. Cafe information is sample content. Photos were cropped from the supplied screenshots; original high-resolution assets can replace them later.

Build and verify:

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
