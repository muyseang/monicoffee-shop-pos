from rest_framework.permissions import BasePermission

from .models import User


class IsClient(BasePermission):
    """Customer-only endpoints (placing and tracking orders). Staff work through
    the dashboard and POS, so a staff token is refused here."""

    message = "Only client accounts can use this endpoint."

    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.role == User.Role.CLIENT)
