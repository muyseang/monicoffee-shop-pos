import re
from datetime import timedelta

from django.contrib import messages
from django.contrib.auth import login as auth_login
from django.contrib.auth import logout as auth_logout
from django.contrib.auth.decorators import login_required
from django.db import transaction
from django.db.models import Count, Q, Sum
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone

from accounts.models import User
from accounts.serializers import normalize_phone
from menu.models import Category, MenuItem
from orders.models import Order
from orders.services import OrderError, advance_order, cancel_order, confirm_payment, place_order, reject_order
from payments.models import PaymentSettings

from .decorators import super_admin_required
from .forms import (
    AdjustStockForm,
    CategoryForm,
    ClientForm,
    ItemOptionFormSet,
    MenuItemForm,
    PaymentSettingsForm,
    RestockForm,
    StaffForm,
    StaffLoginForm,
)


def login_view(request):
    if request.user.is_authenticated:
        return redirect("dashboard:home")

    if request.method == "POST":
        form = StaffLoginForm(request, data=request.POST)
        if form.is_valid():
            auth_login(request, form.get_user())
            return redirect("dashboard:home")
    else:
        form = StaffLoginForm(request)

    return render(request, "dashboard/login.html", {"form": form})


@login_required(login_url="dashboard:login")
def logout_view(request):
    auth_logout(request)
    return redirect("dashboard:login")


@login_required(login_url="dashboard:login")
def home(request):
    return redirect("dashboard:order-list")


@super_admin_required
def staff_list(request):
    query = request.GET.get("q", "").strip()
    status = request.GET.get("status", "")

    staff_qs = User.objects.filter(
        role__in=[User.Role.SUPER_ADMIN, User.Role.STAFF]
    ).order_by("-role", "first_name")

    if query:
        staff_qs = staff_qs.filter(Q(first_name__icontains=query) | Q(username__icontains=query))
    if status == "active":
        staff_qs = staff_qs.filter(is_active=True)
    elif status == "inactive":
        staff_qs = staff_qs.filter(is_active=False)

    return render(
        request,
        "dashboard/staff_list.html",
        {"staff_members": staff_qs, "query": query, "status": status},
    )


@super_admin_required
def staff_create(request):
    if request.method == "POST":
        form = StaffForm(request.POST)
        if form.is_valid():
            form.save()
            messages.success(request, "Staff account created.")
            return redirect("dashboard:staff-list")
    else:
        form = StaffForm()

    return render(request, "dashboard/staff_form.html", {"form": form, "is_create": True})


@super_admin_required
def staff_edit(request, pk):
    staff_member = get_object_or_404(User, pk=pk, role=User.Role.STAFF)

    if request.method == "POST":
        form = StaffForm(
            request.POST,
            instance=staff_member,
            initial={
                "full_name": staff_member.first_name,
                "username": staff_member.username,
                "is_active": staff_member.is_active,
            },
        )
        if form.is_valid():
            form.save()
            messages.success(request, "Staff account updated.")
            return redirect("dashboard:staff-list")
    else:
        form = StaffForm(
            instance=staff_member,
            initial={
                "full_name": staff_member.first_name,
                "username": staff_member.username,
                "is_active": staff_member.is_active,
            },
        )

    return render(
        request,
        "dashboard/staff_form.html",
        {"form": form, "is_create": False, "staff_member": staff_member},
    )


@super_admin_required
def staff_set_active(request, pk, active):
    # Scoped to role=STAFF on purpose: Super Admin accounts (including the
    # caller's own) are never managed through this endpoint, matching UC18 —
    # "An admin cannot delete the account they are logged in with."
    staff_member = get_object_or_404(User, pk=pk, role=User.Role.STAFF)

    if request.method == "POST":
        staff_member.is_active = active
        staff_member.save(update_fields=["is_active"])
        messages.success(
            request,
            f"{staff_member.first_name or staff_member.username} "
            f"{'activated' if active else 'deactivated'}.",
        )

    return redirect("dashboard:staff-list")


# Client management is open to both Super Admin and Staff (UC16), unlike
# Staff management above which is Super Admin only — so these views use
# plain @login_required rather than @super_admin_required.


@login_required(login_url="dashboard:login")
def client_list(request):
    query = request.GET.get("q", "").strip()
    status = request.GET.get("status", "")

    client_qs = User.objects.filter(role=User.Role.CLIENT).order_by("-date_joined")

    if query:
        client_qs = client_qs.filter(Q(first_name__icontains=query) | Q(phone__icontains=query))
    if status == "active":
        client_qs = client_qs.filter(is_active=True)
    elif status == "inactive":
        client_qs = client_qs.filter(is_active=False)

    return render(
        request,
        "dashboard/client_list.html",
        {"clients": client_qs, "query": query, "status": status},
    )


@login_required(login_url="dashboard:login")
def client_create(request):
    if request.method == "POST":
        form = ClientForm(request.POST)
        if form.is_valid():
            form.save()
            messages.success(request, "Client account created.")
            return redirect("dashboard:client-list")
    else:
        form = ClientForm()

    return render(request, "dashboard/client_form.html", {"form": form, "is_create": True})


@login_required(login_url="dashboard:login")
def client_edit(request, pk):
    client = get_object_or_404(User, pk=pk, role=User.Role.CLIENT)

    initial = {
        "full_name": client.first_name,
        "phone": client.phone,
        "address": client.address,
        "is_active": client.is_active,
    }

    if request.method == "POST":
        form = ClientForm(request.POST, instance=client, initial=initial)
        if form.is_valid():
            form.save()
            messages.success(request, "Client account updated.")
            return redirect("dashboard:client-list")
    else:
        form = ClientForm(instance=client, initial=initial)

    return render(
        request, "dashboard/client_form.html", {"form": form, "is_create": False, "client": client}
    )


@login_required(login_url="dashboard:login")
def client_set_active(request, pk, active):
    client = get_object_or_404(User, pk=pk, role=User.Role.CLIENT)

    if request.method == "POST":
        client.is_active = active
        client.save(update_fields=["is_active"])
        messages.success(
            request,
            f"{client.first_name or client.phone} {'activated' if active else 'deactivated'}.",
        )

    return redirect("dashboard:client-list")


# Stock is open to both Super Admin and Staff, like Client management:
# restocking happens during a shift, not only when the Owner is around.


def _menu_item_list_context(request):
    """Search + category + stock-status filters shared by Stock and Menu Items."""
    query = request.GET.get("q", "").strip()
    category = request.GET.get("category", "")
    level = request.GET.get("level", "")

    item_qs = MenuItem.objects.select_related("category").order_by("category__display_order", "category__name", "name")

    if query:
        item_qs = item_qs.filter(name__icontains=query)
    if category.isdigit():
        item_qs = item_qs.filter(category_id=category)
    if level == MenuItem.StockStatus.IN_STOCK:
        item_qs = item_qs.filter(is_available=True, stock__gt=0)
    elif level == MenuItem.StockStatus.OUT_OF_STOCK:
        item_qs = item_qs.filter(is_available=True, stock=0)
    elif level == MenuItem.StockStatus.UNAVAILABLE:
        item_qs = item_qs.filter(is_available=False)

    return {
        "items": item_qs,
        "categories": Category.objects.all(),
        "levels": MenuItem.StockStatus.choices,
        "query": query,
        "category": category,
        "level": level,
    }


@login_required(login_url="dashboard:login")
def stock_list(request):
    return render(request, "dashboard/stock_list.html", _menu_item_list_context(request))


@login_required(login_url="dashboard:login")
def stock_restock(request, pk):
    item = get_object_or_404(MenuItem, pk=pk)

    if request.method == "POST":
        form = RestockForm(request.POST)
        if form.is_valid():
            item.restock(form.cleaned_data["quantity"])
            messages.success(request, f"{item.name} restocked. New stock: {item.stock}.")
        else:
            messages.error(request, f"{item.name} not restocked: {form.errors['quantity'][0]}")

    return redirect("dashboard:stock-list")


@login_required(login_url="dashboard:login")
def stock_adjust(request, pk):
    item = get_object_or_404(MenuItem, pk=pk)

    if request.method == "POST":
        form = AdjustStockForm(request.POST)
        if form.is_valid():
            item.stock = form.cleaned_data["quantity"]
            item.save(update_fields=["stock"])
            messages.success(request, f"{item.name} stock set to {item.stock}.")
        else:
            messages.error(request, f"{item.name} not adjusted: {form.errors['quantity'][0]}")

    return redirect("dashboard:stock-list")


@login_required(login_url="dashboard:login")
def stock_set_visible(request, pk, visible):
    item = get_object_or_404(MenuItem, pk=pk)

    if request.method == "POST":
        item.is_available = visible
        item.save(update_fields=["is_available"])
        messages.success(
            request,
            f"{item.name} {'shown to' if visible else 'hidden from'} customers.",
        )

    return redirect("dashboard:stock-list")


# Categories are part of menu management, which the proposal (4.1.1) gives
# to both Super Admin and Staff.


@login_required(login_url="dashboard:login")
def category_list(request):
    query = request.GET.get("q", "").strip()

    category_qs = Category.objects.annotate(item_count=Count("items"))
    if query:
        category_qs = category_qs.filter(name__icontains=query)

    return render(request, "dashboard/category_list.html", {"categories": category_qs, "query": query})


@login_required(login_url="dashboard:login")
def category_create(request):
    if request.method == "POST":
        form = CategoryForm(request.POST)
        if form.is_valid():
            form.save()
            messages.success(request, "Category created.")
            return redirect("dashboard:category-list")
    else:
        form = CategoryForm()

    return render(request, "dashboard/category_form.html", {"form": form, "is_create": True})


@login_required(login_url="dashboard:login")
def category_edit(request, pk):
    category = get_object_or_404(Category, pk=pk)

    if request.method == "POST":
        form = CategoryForm(request.POST, instance=category)
        if form.is_valid():
            form.save()
            messages.success(request, "Category updated.")
            return redirect("dashboard:category-list")
    else:
        form = CategoryForm(instance=category)

    return render(
        request, "dashboard/category_form.html", {"form": form, "is_create": False, "category": category}
    )


@login_required(login_url="dashboard:login")
def category_delete(request, pk):
    category = get_object_or_404(Category, pk=pk)

    if request.method == "POST":
        # MenuItem.category is PROTECT, so a category still in use can't be
        # deleted — say why here instead of letting the database refuse.
        item_count = category.items.count()
        if item_count:
            messages.error(
                request,
                f"{category.name} still has {item_count} menu item{'s' if item_count != 1 else ''}. "
                "Move or delete them first.",
            )
        else:
            category.delete()
            messages.success(request, f"{category.name} deleted.")

    return redirect("dashboard:category-list")


# Menu items are open to both Super Admin and Staff (proposal 4.1.1).


@login_required(login_url="dashboard:login")
def menu_item_list(request):
    return render(request, "dashboard/menu_item_list.html", _menu_item_list_context(request))


def _menu_item_form_view(request, item=None):
    if request.method == "POST":
        form = MenuItemForm(request.POST, request.FILES, instance=item)
        option_formset = ItemOptionFormSet(request.POST, instance=form.instance)
        if form.is_valid() and option_formset.is_valid():
            # One transaction, so a failure while saving options can't leave
            # behind an item with only half of its options.
            with transaction.atomic():
                item = form.save()
                option_formset.instance = item
                option_formset.save()
            messages.success(request, f"{item.name} saved.")
            return redirect("dashboard:menu-item-list")
    else:
        form = MenuItemForm(instance=item)
        option_formset = ItemOptionFormSet(instance=item)

    return render(
        request,
        "dashboard/menu_item_form.html",
        {"form": form, "option_formset": option_formset, "is_create": item is None, "item": item},
    )


@login_required(login_url="dashboard:login")
def menu_item_create(request):
    return _menu_item_form_view(request)


@login_required(login_url="dashboard:login")
def menu_item_edit(request, pk):
    return _menu_item_form_view(request, get_object_or_404(MenuItem, pk=pk))


@login_required(login_url="dashboard:login")
def menu_item_delete(request, pk):
    item = get_object_or_404(MenuItem, pk=pk)

    if request.method == "POST":
        # Options are deleted with the item (CASCADE). The image file has to be
        # removed by hand — deleting the row doesn't touch MEDIA_ROOT.
        image = item.image
        item.delete()
        if image:
            image.delete(save=False)
        messages.success(request, f"{item.name} deleted.")

    return redirect("dashboard:menu-item-list")


# Settings decide where customers' money goes, so they're Owner only.


@super_admin_required
def payment_settings(request):
    instance = PaymentSettings.load()

    if request.method == "POST":
        form = PaymentSettingsForm(request.POST, request.FILES, instance=instance)
        if form.is_valid():
            with transaction.atomic():
                form.save()
            messages.success(request, "Payment settings saved.")
            return redirect("dashboard:settings")
    else:
        form = PaymentSettingsForm(instance=instance)

    # A rejected POST has already copied its unsaved upload onto `instance`,
    # so show the current QR from a fresh read of the saved row.
    return render(request, "dashboard/settings.html", {"form": form, "saved": PaymentSettings.load()})


# Orders and POS are day-to-day operations, open to both Super Admin and Staff.

ORDER_DATE_RANGES = {"today": "Today", "yesterday": "Yesterday", "week": "Last 7 days", "all": "All dates"}

# Button label for moving an order to each next status.
ADVANCE_LABELS = {
    Order.Status.ACCEPTED: "Accept Order",
    Order.Status.PREPARING: "Start Preparing",
    Order.Status.READY: "Mark Ready",
    Order.Status.COMPLETED: "Mark Completed",
}


def _filtered_orders(request):
    query = request.GET.get("q", "").strip()
    date_range = request.GET.get("date", "today")
    status = request.GET.get("status", "")
    order_type = request.GET.get("type", "")

    # Explicit order_by: Django drops Meta.ordering from aggregated (GROUP BY) queries.
    order_qs = (
        Order.objects.select_related("client")
        .annotate(item_count=Sum("items__quantity"))
        .order_by("-created_at", "-pk")
    )

    if query:
        # "#MC-0146", "MC-0146" and "146" all find order 146.
        number = re.fullmatch(r"#?(?:MC-)?0*(\d+)", query, flags=re.IGNORECASE)
        name_match = Q(client__first_name__icontains=query)
        order_qs = order_qs.filter(name_match | Q(pk=number.group(1)) if number else name_match)

    # Ranges from local midnight rather than created_at__date: MySQL can only
    # do __date in local time when its timezone tables are loaded.
    midnight = timezone.localtime().replace(hour=0, minute=0, second=0, microsecond=0)
    if date_range == "today":
        order_qs = order_qs.filter(created_at__gte=midnight)
    elif date_range == "yesterday":
        order_qs = order_qs.filter(created_at__gte=midnight - timedelta(days=1), created_at__lt=midnight)
    elif date_range == "week":
        order_qs = order_qs.filter(created_at__gte=midnight - timedelta(days=6))
    else:
        date_range = "all"

    if status in Order.Status.values:
        order_qs = order_qs.filter(status=status)
    if order_type in Order.OrderType.values:
        order_qs = order_qs.filter(order_type=order_type)

    return {
        "orders": order_qs[:200],
        "query": query,
        "date_range": date_range,
        "date_ranges": ORDER_DATE_RANGES,
        "status": status,
        "statuses": Order.Status.choices,
        "order_type": order_type,
        "order_types": Order.OrderType.choices,
    }


@login_required(login_url="dashboard:login")
def order_list(request):
    context = _filtered_orders(request)
    # The 15-second auto-refresh asks for just the table.
    template = "dashboard/_order_table.html" if request.GET.get("partial") else "dashboard/order_list.html"
    return render(request, template, context)


def _order_steps(order):
    """The status stepper: the normal flow, or Pending → Rejected/Cancelled."""
    if order.status in (Order.Status.REJECTED, Order.Status.CANCELLED):
        return [
            {"label": "Pending", "state": "done"},
            {"label": order.get_status_display(), "state": "stopped"},
        ]
    current = Order.FLOW.index(order.status)
    steps = []
    for index, status in enumerate(Order.FLOW):
        if index < current or order.status == Order.Status.COMPLETED:
            state = "done"
        elif index == current:
            state = "current"
        else:
            state = "todo"
        steps.append({"label": status.label, "state": state})
    return steps


def _time_label(value):
    value = timezone.localtime(value)
    day = "today" if value.date() == timezone.localdate() else value.strftime("%d %b %Y")
    return f"{day} {value:%H:%M}"


@login_required(login_url="dashboard:login")
def order_detail(request, pk):
    order = get_object_or_404(Order.objects.select_related("client"), pk=pk)
    items = list(order.items.all())
    return render(
        request,
        "dashboard/order_detail.html",
        {
            "order": order,
            "items": items,
            "item_count": sum(line.quantity for line in items),
            "steps": _order_steps(order),
            "placed_label": _time_label(order.created_at),
            "rejected_time": timezone.localtime(order.rejected_at) if order.rejected_at else None,
            "advance_label": ADVANCE_LABELS.get(order.next_status),
        },
    )


def _order_action(request, pk, action, success_message):
    order = get_object_or_404(Order, pk=pk)
    if request.method == "POST":
        try:
            order = action(order)
            messages.success(request, success_message(order))
        except OrderError as error:
            messages.error(request, str(error))
    return order


@login_required(login_url="dashboard:login")
def order_advance(request, pk):
    _order_action(
        request,
        pk,
        lambda order: advance_order(order, request.POST.get("from", "")),
        lambda order: f"Order #{order.number} is now {order.get_status_display().lower()}.",
    )
    return redirect("dashboard:order-detail", pk=pk)


@login_required(login_url="dashboard:login")
def order_reject(request, pk):
    _order_action(
        request,
        pk,
        lambda order: reject_order(order, request.POST.get("reason", "")),
        lambda order: f"Order #{order.number} rejected. Stock was returned.",
    )
    return redirect("dashboard:order-detail", pk=pk)


@login_required(login_url="dashboard:login")
def order_confirm_payment(request, pk):
    _order_action(
        request, pk, confirm_payment, lambda order: f"QR payment for #{order.number} confirmed."
    )
    return redirect("dashboard:order-detail", pk=pk)


def _parse_cart(values):
    """POS cart lines arrive as repeated `item=<menu item id>:<quantity>`."""
    lines = []
    for value in values:
        item_id, _, quantity = value.partition(":")
        if item_id.isdigit() and quantity.isdigit():
            lines.append((int(item_id), int(quantity)))
    return lines


@login_required(login_url="dashboard:login")
def pos(request):
    lines = []
    form_values = {"order_type": Order.OrderType.DINE_IN, "payment_method": Order.PaymentMethod.CASH}

    if request.method == "POST":
        lines = _parse_cart(request.POST.getlist("item"))
        form_values = {
            "order_type": request.POST.get("order_type", ""),
            "table_number": request.POST.get("table_number", ""),
            "client": request.POST.get("client", "").strip(),
            "payment_method": request.POST.get("payment_method", ""),
        }
        try:
            if form_values["order_type"] not in Order.OrderType.values:
                raise OrderError("Choose Dine-in or Pickup.")
            if form_values["payment_method"] not in Order.PaymentMethod.values:
                raise OrderError("Choose a payment method.")
            client = None
            if form_values["client"]:
                client = User.objects.filter(
                    role=User.Role.CLIENT, is_active=True, phone=normalize_phone(form_values["client"])
                ).first()
                if client is None:
                    raise OrderError("No active client account has that phone number. Leave it empty for a walk-in.")
            order = place_order(
                lines=lines,
                order_type=form_values["order_type"],
                table_number=form_values["table_number"],
                payment_method=form_values["payment_method"],
                source=Order.Source.POS,
                client=client,
                created_by=request.user,
            )
        except OrderError as error:
            messages.error(request, str(error))
        else:
            if order.payment_method == Order.PaymentMethod.MANUAL_QR:
                return redirect("dashboard:pos-pay", pk=order.pk)
            return redirect("dashboard:pos-receipt", pk=order.pk)

    items = list(MenuItem.objects.select_related("category").order_by("category__display_order", "name"))
    return render(
        request,
        "dashboard/pos.html",
        {
            "categories": Category.objects.all(),
            "items": items,
            # Price and stock for the cart's own arithmetic; the server re-checks both.
            "menu_data": {
                str(item.pk): {"name": item.name, "price": float(item.price), "stock": item.stock if item.in_stock else 0}
                for item in items
            },
            "clients": User.objects.filter(role=User.Role.CLIENT, is_active=True).order_by("first_name"),
            "qr_available": PaymentSettings.load().qr_available,
            "form_values": form_values,
            # A rejected checkout comes back with the cart still filled in.
            "initial_cart": [{"id": item_id, "qty": quantity} for item_id, quantity in lines],
            "show_checkout": request.method == "POST",
        },
    )


@login_required(login_url="dashboard:login")
def pos_pay(request, pk):
    order = get_object_or_404(Order, pk=pk, payment_method=Order.PaymentMethod.MANUAL_QR)
    if order.payment_status == Order.PaymentStatus.PAID or order.is_final:
        return redirect("dashboard:pos-receipt", pk=order.pk)
    return render(request, "dashboard/pos_pay.html", {"order": order, "payment_settings": PaymentSettings.load()})


@login_required(login_url="dashboard:login")
def pos_confirm_payment(request, pk):
    _order_action(request, pk, confirm_payment, lambda order: f"Payment for #{order.number} received.")
    return redirect("dashboard:pos-receipt", pk=pk)


@login_required(login_url="dashboard:login")
def pos_cancel(request, pk):
    _order_action(
        request, pk, cancel_order, lambda order: f"Order #{order.number} cancelled. Stock was returned."
    )
    return redirect("dashboard:pos")


@login_required(login_url="dashboard:login")
def pos_receipt(request, pk):
    order = get_object_or_404(Order, pk=pk)
    return render(
        request,
        "dashboard/pos_receipt.html",
        {"order": order, "items": order.items.all(), "placed_label": _time_label(order.created_at)},
    )
