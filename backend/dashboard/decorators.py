from functools import wraps

from django.contrib.auth.decorators import login_required
from django.core.exceptions import PermissionDenied

from accounts.models import User


def super_admin_required(view_func):
    @wraps(view_func)
    @login_required(login_url="dashboard:login")
    def wrapper(request, *args, **kwargs):
        if request.user.role != User.Role.SUPER_ADMIN:
            raise PermissionDenied("Only the Owner / Super Admin can access this page.")
        return view_func(request, *args, **kwargs)

    return wrapper
