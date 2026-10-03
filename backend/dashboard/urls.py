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
]
