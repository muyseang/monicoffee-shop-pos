from accounts.models import User
from orders.models import Order


def pending_orders(request):
    """How many orders are still Pending, for the count next to "Orders" in the sidebar.

    Only on dashboard pages for signed-in staff, so the API and the login page skip the query.
    """
    user = request.user
    if not request.path.startswith("/dashboard/") or not user.is_authenticated or user.role == User.Role.CLIENT:
        return {}
    return {"pending_order_count": pending_order_count()}


def pending_order_count():
    # Every date, not only today's list: an order left Pending overnight still needs someone.
    return Order.objects.filter(status=Order.Status.PENDING).count()
