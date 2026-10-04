from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path
from django.views.generic import RedirectView

urlpatterns = [
    path("", RedirectView.as_view(pattern_name="dashboard:login", permanent=False)),
    path("admin/", admin.site.urls),
    path("api/auth/", include("accounts.urls")),
    path("dashboard/", include("dashboard.urls")),
]

# Uploaded menu images. static() only adds this route while DEBUG is on;
# a production server serves MEDIA_ROOT itself.
urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
