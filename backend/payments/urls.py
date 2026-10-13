from django.urls import path

from . import views

urlpatterns = [
    path("methods/", views.PaymentMethodsView.as_view(), name="payment-methods"),
]
