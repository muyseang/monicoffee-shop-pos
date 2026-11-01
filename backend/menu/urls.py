from django.urls import path

from . import views

urlpatterns = [
    path("categories/", views.CategoryListView.as_view(), name="menu-categories"),
    path("items/", views.MenuItemListView.as_view(), name="menu-items"),
    path("items/<int:pk>/", views.MenuItemDetailView.as_view(), name="menu-item-detail"),
]
