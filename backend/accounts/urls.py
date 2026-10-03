from django.urls import path

from . import views

urlpatterns = [
    path("register/request-otp/", views.RegisterRequestOTPView.as_view(), name="register-request-otp"),
    path("register/verify-otp/", views.RegisterVerifyOTPView.as_view(), name="register-verify-otp"),
    path("register/status/", views.RegisterStatusView.as_view(), name="register-status"),
    path("login/request-otp/", views.LoginRequestOTPView.as_view(), name="login-request-otp"),
    path("login/verify-otp/", views.LoginVerifyOTPView.as_view(), name="login-verify-otp"),
    path("login/status/", views.LoginStatusView.as_view(), name="login-status"),
    path("logout/", views.LogoutView.as_view(), name="logout"),
]
