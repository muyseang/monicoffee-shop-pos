from django.contrib import messages
from django.contrib.auth import login as auth_login
from django.contrib.auth import logout as auth_logout
from django.contrib.auth.decorators import login_required
from django.db import transaction
from django.db.models import Count, Q
from django.shortcuts import get_object_or_404, redirect, render

from accounts.models import User
from menu.models import Category, MenuItem

from .decorators import super_admin_required
from .forms import (
    AdjustStockForm,
    CategoryForm,
    ClientForm,
    ItemOptionFormSet,
    MenuItemForm,
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
