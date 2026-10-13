from django.contrib import admin

from .models import PaymentSettings


@admin.register(PaymentSettings)
class PaymentSettingsAdmin(admin.ModelAdmin):
    list_display = ("__str__", "qr_enabled", "account_name", "updated_at")

    def has_add_permission(self, request):
        # Single row (pk=1) — it's edited, never added.
        return not PaymentSettings.objects.exists()

    def has_delete_permission(self, request, obj=None):
        return False
