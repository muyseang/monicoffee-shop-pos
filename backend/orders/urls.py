from django.urls import path

from . import views

urlpatterns = [
    path("", views.OrderListCreateView.as_view(), name="orders"),
    path("<int:pk>/", views.OrderDetailView.as_view(), name="order-detail"),
    path("<int:pk>/cancel/", views.CancelOrderView.as_view(), name="order-cancel"),
    path("<int:pk>/payment-proof/", views.PaymentProofView.as_view(), name="order-payment-proof"),
]
