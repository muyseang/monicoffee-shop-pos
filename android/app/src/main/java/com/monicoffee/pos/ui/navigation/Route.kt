package com.monicoffee.pos.ui.navigation

import com.monicoffee.pos.MainActivity
import com.monicoffee.pos.ui.Screen
import com.monicoffee.pos.ui.auth.AuthScreen
import com.monicoffee.pos.ui.auth.OnboardingScreen
import com.monicoffee.pos.ui.auth.RegisteredScreen
import com.monicoffee.pos.ui.auth.SplashScreen
import com.monicoffee.pos.ui.auth.VerificationScreen
import com.monicoffee.pos.ui.auth.VerifyingScreen
import com.monicoffee.pos.ui.cart.CartScreen
import com.monicoffee.pos.ui.cart.CheckoutScreen
import com.monicoffee.pos.ui.cart.OrderPlacedScreen
import com.monicoffee.pos.ui.menu.CafeScreen
import com.monicoffee.pos.ui.menu.DetailsScreen
import com.monicoffee.pos.ui.menu.ExploreScreen
import com.monicoffee.pos.ui.menu.FavoritesScreen
import com.monicoffee.pos.ui.menu.HomeScreen
import com.monicoffee.pos.ui.orders.OrderDetailsScreen
import com.monicoffee.pos.ui.orders.OrdersScreen
import com.monicoffee.pos.ui.orders.PayScreen
import com.monicoffee.pos.ui.orders.TrackOrderScreen
import com.monicoffee.pos.ui.orders.UploadProofScreen
import com.monicoffee.pos.ui.profile.PaymentMethodScreen
import com.monicoffee.pos.ui.profile.PersonalInfoScreen
import com.monicoffee.pos.ui.profile.ProfileScreen

/** Every screen of the app. */
enum class Route {
    SPLASH, ONBOARDING, LOGIN, REGISTER, VERIFICATION, VERIFYING, REGISTERED,
    HOME, EXPLORE, FAVORITES, DETAILS, CAFE,
    CART, CHECKOUT, ORDER_PLACED,
    ORDERS, TRACK_ORDER, ORDER_DETAILS, PAY, UPLOAD_PROOF,
    PROFILE, PERSONAL_INFO, PAYMENT_METHOD;

    /** Builds the screen that draws this route. */
    fun create(app: MainActivity): Screen = when (this) {
        SPLASH -> SplashScreen(app)
        ONBOARDING -> OnboardingScreen(app)
        LOGIN -> AuthScreen(app, register = false)
        REGISTER -> AuthScreen(app, register = true)
        VERIFICATION -> VerificationScreen(app)
        VERIFYING -> VerifyingScreen(app)
        REGISTERED -> RegisteredScreen(app)
        HOME -> HomeScreen(app)
        EXPLORE -> ExploreScreen(app)
        FAVORITES -> FavoritesScreen(app)
        DETAILS -> DetailsScreen(app)
        CAFE -> CafeScreen(app)
        CART -> CartScreen(app)
        CHECKOUT -> CheckoutScreen(app)
        ORDER_PLACED -> OrderPlacedScreen(app)
        ORDERS -> OrdersScreen(app)
        TRACK_ORDER -> TrackOrderScreen(app)
        ORDER_DETAILS -> OrderDetailsScreen(app)
        PAY -> PayScreen(app)
        UPLOAD_PROOF -> UploadProofScreen(app)
        PROFILE -> ProfileScreen(app)
        PERSONAL_INFO -> PersonalInfoScreen(app)
        PAYMENT_METHOD -> PaymentMethodScreen(app)
    }
}
