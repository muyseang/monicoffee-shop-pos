from django.urls import path

from . import views

app_name = "dashboard"

urlpatterns = [
    path("login/", views.login_view, name="login"),
    path("logout/", views.logout_view, name="logout"),
    path("", views.home, name="home"),
    path("staff/", views.staff_list, name="staff-list"),
    path("staff/add/", views.staff_create, name="staff-create"),
    path("staff/<int:pk>/edit/", views.staff_edit, name="staff-edit"),
    path("staff/<int:pk>/deactivate/", views.staff_set_active, {"active": False}, name="staff-deactivate"),
    path("staff/<int:pk>/activate/", views.staff_set_active, {"active": True}, name="staff-activate"),
    path("clients/", views.client_list, name="client-list"),
    path("clients/add/", views.client_create, name="client-create"),
    path("clients/<int:pk>/edit/", views.client_edit, name="client-edit"),
    path("clients/<int:pk>/deactivate/", views.client_set_active, {"active": False}, name="client-deactivate"),
    path("clients/<int:pk>/activate/", views.client_set_active, {"active": True}, name="client-activate"),
    path("menu-items/", views.menu_item_list, name="menu-item-list"),
    path("menu-items/add/", views.menu_item_create, name="menu-item-create"),
    path("menu-items/<int:pk>/edit/", views.menu_item_edit, name="menu-item-edit"),
    path("menu-items/<int:pk>/delete/", views.menu_item_delete, name="menu-item-delete"),
    path("categories/", views.category_list, name="category-list"),
    path("categories/add/", views.category_create, name="category-create"),
    path("categories/<int:pk>/edit/", views.category_edit, name="category-edit"),
    path("categories/<int:pk>/delete/", views.category_delete, name="category-delete"),
    path("stock/", views.stock_list, name="stock-list"),
    path("stock/<int:pk>/restock/", views.stock_restock, name="stock-restock"),
    path("stock/<int:pk>/adjust/", views.stock_adjust, name="stock-adjust"),
    path("stock/<int:pk>/show/", views.stock_set_visible, {"visible": True}, name="stock-show"),
    path("stock/<int:pk>/hide/", views.stock_set_visible, {"visible": False}, name="stock-hide"),
]
