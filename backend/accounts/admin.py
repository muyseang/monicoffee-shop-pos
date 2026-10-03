from django.contrib import admin
from django.contrib.auth.admin import UserAdmin

from .models import PhoneVerification, User


@admin.register(User)
class CustomUserAdmin(UserAdmin):
    fieldsets = UserAdmin.fieldsets + (("Coffee shop", {"fields": ("role", "phone", "address")}),)
    list_display = ("username", "email", "role", "is_active")
    list_filter = ("role", "is_active")


@admin.register(PhoneVerification)
class PhoneVerificationAdmin(admin.ModelAdmin):
    list_display = ("phone", "purpose", "code", "is_used", "attempts", "created_at", "expires_at")
    list_filter = ("purpose", "is_used")
