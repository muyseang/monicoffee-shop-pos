# Moni Coffee Android client

Native Android app (Kotlin, no third-party libraries) for customers of Moni Coffee. The screens follow the PNG references in `../Android-UI/Moni coffee` and all data comes from the Django backend in `../backend`.

## Code layout

Everything is under `app/src/main/java/com/monicoffee/pos/`. One activity hosts the whole app; each screen is its own class that draws itself in code from the shared app state.

| Package | What it holds |
|---|---|
| `MainActivity.kt` | The only activity. Wires the parts together and forwards lifecycle, Back and the picture chooser. |
| `core/` | `AppConfig` (constants), `AppState` (session, menu, cart, checkout, orders), `SessionManager` (sign in / out), `TaskRunner` (background calls, polling). |
| `data/api/` | `ApiClient` — one method per backend endpoint — plus `ApiException` and the emulator-aware `connect()`. |
| `data/model/` | The data classes the API returns (`MenuItem`, `Order`, `Profile`, …) and `CartItem`. |
| `data/local/` | `Preferences` — what is saved on the phone (token, profile, favorites, server address). |
| `data/image/` | `ImageLoader` — downloads and caches menu photos and the shop's QR code. |
| `ui/Screen.kt` | Base class of every screen: header, scrolling content, footer, bottom navigation. |
| `ui/navigation/` | `Route` (the list of screens) and `Router` (which screen is shown, back stack). |
| `ui/theme/`, `ui/widget/` | `Palette` (colours), `Widgets` (buttons, fields, chips, empty and error states), `Icon` (drawn icons). |
| `ui/auth/` | Splash, onboarding, log in / sign up, OTP verification. |
| `ui/menu/` | Home, Explore, Favorites, item details, cafe. |
| `ui/cart/` | Cart, checkout, order placed. |
| `ui/orders/` | My Orders, track order, order details, QR payment, upload screenshot. |
| `ui/profile/` | Profile, personal info, payment method. |

To add a screen: create a class extending `Screen` in the matching `ui/` package, add an entry to `Route` and map it in `Route.create`.

## What is connected to the backend

| Screen | Endpoint |
|---|---|
| Login / Register / OTP verification | `POST /api/auth/{login,register}/request-otp/`, `.../verify-otp/`, `GET .../status/` (polled, so a code confirmed by the shop's SMS device signs the app in) |
| Home, Explore, Favorites, Details | `GET /api/menu/categories/`, `GET /api/menu/items/` (photos, options, out-of-stock) |
| Checkout | `GET /api/payments/methods/`, `POST /api/orders/` |
| QR payment | `GET /api/payments/methods/`, `POST /api/orders/<id>/payment-proof/` |
| My Orders, Transaction Details | `GET /api/orders/?status=…`, `GET /api/orders/<id>/` (refreshed every 15 s), `POST /api/orders/<id>/cancel/` |
| Profile, Personal Info | `GET` / `PATCH /api/auth/me/`, `POST /api/auth/logout/` |

Kept on the phone only: the cart (in memory), favorites and the preferred payment method (SharedPreferences) — the backend has no API for them. The Cafe screen is sample content.

Prices and totals shown before checkout are for display; the server recalculates every order from its own prices.

## Running against the backend

The backend is used exactly as it is in `../backend` — no backend code or settings need to change for the emulator.

1. Start the backend as its README says (`python manage.py runserver`).
2. Run the app:
   - **Emulator:** nothing to configure. The app's default server is `http://10.0.2.2:8000`, the emulator's address for this computer.
   - **Real phone over USB:** run `adb reverse tcp:8000 tcp:8000`, then in the app tap **Server: … (change)** on the login screen and enter `http://127.0.0.1:8000`.
   - **Real phone over Wi-Fi:** start the backend with `python manage.py runserver 0.0.0.0:8000`, add the computer's IP address to `ALLOWED_HOSTS` in your own `backend/.env` (a local file, not committed), and enter `http://<that IP>:8000` as the server in the app.
3. Log in with the seeded client phone number `012345678`, or register a new number. There is no SMS gateway yet: while the backend runs with `DEBUG=True` it returns the code, and the app shows it on the verification screen.

QR payment appears at checkout only after the owner enables it and uploads a QR image in the dashboard settings.

## Build and verify

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
