from django.contrib import messages
from django.contrib.auth import login as auth_login
from django.contrib.auth import logout as auth_logout
from django.contrib.auth.decorators import login_required
from django.db.models import Q
from django.shortcuts import get_object_or_404, redirect, render

from accounts.models import User
from menu.models import Category, MenuItem

from .decorators import super_admin_required
from .forms import AdjustStockForm, ClientForm, RestockForm, StaffForm, StaffLoginForm


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
    if request.user.role == User.Role.SUPER_ADMIN:
        return redirect("dashboard:staff-list")
    return redirect("dashboard:client-list")


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


@login_required(login_url="dashboard:login")
def stock_list(request):
    query = request.GET.get("q", "").strip()
    category = request.GET.get("category", "")
    level = request.GET.get("level", "")

    item_qs = MenuItem.objects.select_related("category").order_by("category__name", "name")

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

    return render(
        request,
        "dashboard/stock_list.html",
        {
            "items": item_qs,
            "categories": Category.objects.order_by("name"),
            "levels": MenuItem.StockStatus.choices,
            "query": query,
            "category": category,
            "level": level,
        },
    )


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
