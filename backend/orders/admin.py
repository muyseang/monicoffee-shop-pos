from django.contrib import admin

from .models import Order, OrderItem


class OrderItemInline(admin.TabularInline):
    model = OrderItem
    extra = 0
    readonly_fields = ("menu_item", "name", "unit_price", "quantity", "options")


@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    # Read-mostly: changing status here would skip the stock bookkeeping in
    # orders.services, so status changes belong in the dashboard.
    list_display = ("__str__", "client", "order_type", "payment_method", "status", "total", "created_at")
    list_filter = ("status", "order_type", "payment_method", "source")
    readonly_fields = ("status", "payment_status", "total", "rejected_at", "created_at")
    inlines = [OrderItemInline]
