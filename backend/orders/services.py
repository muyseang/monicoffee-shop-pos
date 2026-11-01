"""Every change to an order goes through here, so stock and status stay in step
no matter who makes the change (POS, dashboard, or later the mobile app API)."""

from decimal import Decimal

from django.db import transaction
from django.db.models import F
from django.utils import timezone

from menu.models import MenuItem
from payments.models import PaymentSettings

from .models import Order, OrderItem


class OrderError(Exception):
    """A business rule stopped the change; the message is safe to show staff."""


@transaction.atomic
def place_order(
    *, lines, order_type, payment_method, source, table_number="", pickup_time=None, client=None, created_by=None
):
    """Create an order and deduct its stock.

    `lines` is a list of (menu_item_id, quantity), optionally followed by an
    options label and the options' extra price per unit — the app sends e.g.
    (7, 1, "Size: Medium · Extra Shot", Decimal("0.50")). Lines for the same
    item with the same options are merged; different options stay separate.
    The menu rows are locked until the transaction ends, so two tills selling
    the last croissant at the same moment can't both succeed.
    """
    merged = {}
    for item_id, quantity, *extras in lines:
        options, extra_price = (list(extras) + ["", Decimal("0")])[:2]
        if quantity > 0:
            key = (item_id, options, extra_price)
            merged[key] = merged.get(key, 0) + quantity
    if not merged:
        raise OrderError("Add at least one item to the order.")

    # Stock is per menu item, whatever the options: check the combined amount.
    quantities = {}
    for (item_id, _, _), quantity in merged.items():
        quantities[item_id] = quantities.get(item_id, 0) + quantity

    if order_type == Order.OrderType.DINE_IN and not table_number.strip():
        raise OrderError("Table number is required for dine-in orders.")
    if payment_method == Order.PaymentMethod.MANUAL_QR and not PaymentSettings.load().qr_available:
        raise OrderError("Manual QR payments are turned off. Choose Cash, or set up the QR in Settings.")

    menu_items = MenuItem.objects.select_for_update().in_bulk(quantities.keys())
    for item_id, quantity in quantities.items():
        item = menu_items.get(item_id)
        if item is None:
            raise OrderError("One of the items is no longer on the menu.")
        if not item.is_available or item.stock == 0:
            raise OrderError(f"{item.name} is out of stock.")
        if item.stock < quantity:
            raise OrderError(f"Only {item.stock} {item.name} left.")

    order = Order.objects.create(
        client=client,
        created_by=created_by,
        source=source,
        order_type=order_type,
        table_number=table_number.strip() if order_type == Order.OrderType.DINE_IN else "",
        pickup_time=pickup_time if order_type == Order.OrderType.PICKUP else None,
        payment_method=payment_method,
        payment_status=(
            Order.PaymentStatus.AWAITING
            if payment_method == Order.PaymentMethod.MANUAL_QR
            else Order.PaymentStatus.NOT_PAID
        ),
        total=sum(
            (menu_items[item_id].price + extra_price) * quantity
            for (item_id, _, extra_price), quantity in merged.items()
        ),
    )
    OrderItem.objects.bulk_create(
        OrderItem(order=order, menu_item=menu_items[item_id], name=menu_items[item_id].name,
                  unit_price=menu_items[item_id].price + extra_price, quantity=quantity, options=options)
        for (item_id, options, extra_price), quantity in merged.items()
    )
    for item_id, quantity in quantities.items():
        MenuItem.objects.filter(pk=item_id).update(stock=F("stock") - quantity)
    return order


def _lock(order):
    # Re-read under a row lock: two staff clicking at once must not both act
    # on the same old status (e.g. reject twice and return stock twice).
    return Order.objects.select_for_update().get(pk=order.pk)


def _close_unfilled(order, status, fields):
    """Shared by reject and cancel: the order will never be made, so its stock
    goes back and an unpaid QR order stops waiting for a payment."""
    order.status = status
    fields = [*fields, "status", "updated_at"]
    if order.payment_status == Order.PaymentStatus.AWAITING:
        order.payment_status = Order.PaymentStatus.NOT_PAID
        fields.append("payment_status")
    order.save(update_fields=fields)
    for line in order.items.exclude(menu_item=None):
        MenuItem.objects.filter(pk=line.menu_item_id).update(stock=F("stock") + line.quantity)


@transaction.atomic
def reject_order(order, reason):
    reason = reason.strip()
    if not reason:
        raise OrderError("A reason is required to reject an order.")
    order = _lock(order)
    if order.status != Order.Status.PENDING:
        raise OrderError(f"Only pending orders can be rejected. This one is {order.get_status_display().lower()}.")
    order.rejection_reason = reason
    order.rejected_at = timezone.now()
    _close_unfilled(order, Order.Status.REJECTED, ["rejection_reason", "rejected_at"])
    return order


@transaction.atomic
def cancel_order(order):
    order = _lock(order)
    if order.is_final:
        raise OrderError(f"This order is already {order.get_status_display().lower()}.")
    _close_unfilled(order, Order.Status.CANCELLED, [])
    return order


@transaction.atomic
def advance_order(order, expected_status):
    """Move to the next step. `expected_status` is the status the staff member
    saw on screen; if someone else moved the order meanwhile, refuse rather
    than skip a step."""
    order = _lock(order)
    if order.status != expected_status or order.next_status is None:
        raise OrderError("This order was updated by someone else. Check its current status.")
    order.status = order.next_status
    fields = ["status", "updated_at"]
    # Cash is collected at the counter when the order is handed over.
    if order.status == Order.Status.COMPLETED and order.payment_method == Order.PaymentMethod.CASH:
        order.payment_status = Order.PaymentStatus.PAID
        fields.append("payment_status")
    order.save(update_fields=fields)
    return order


@transaction.atomic
def confirm_payment(order):
    order = _lock(order)
    if not order.can_confirm_payment:
        raise OrderError("There is no QR payment waiting for confirmation on this order.")
    order.payment_status = Order.PaymentStatus.PAID
    order.save(update_fields=["payment_status", "updated_at"])
    return order
