from django.db import transaction
from django.shortcuts import get_object_or_404
from rest_framework import status
from rest_framework.parsers import MultiPartParser
from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.permissions import IsClient

from .models import Order
from .serializers import OrderSerializer, PaymentProofSerializer, PlaceOrderSerializer
from .services import OrderError, cancel_order, place_order

# Client side of the order flow (proposal 4.1.6). Every view is scoped to the
# caller's own orders, so another client's order id simply 404s (acceptance
# criteria: "A customer can only view their own orders").

ORDER_GROUPS = {
    # The app's My Orders tabs.
    "active": [Order.Status.PENDING, Order.Status.ACCEPTED, Order.Status.PREPARING, Order.Status.READY],
    "completed": [Order.Status.COMPLETED],
    "cancelled": [Order.Status.REJECTED, Order.Status.CANCELLED],
}


def _own_orders(request):
    return Order.objects.filter(client=request.user).prefetch_related("items__menu_item")


def _own_order(request, pk):
    return get_object_or_404(_own_orders(request), pk=pk)


def _error(message):
    return Response({"detail": message}, status=status.HTTP_400_BAD_REQUEST)


class OrderListCreateView(APIView):
    permission_classes = [IsClient]

    def get(self, request):
        orders = _own_orders(request)
        group = request.query_params.get("status", "")
        if group in ORDER_GROUPS:
            orders = orders.filter(status__in=ORDER_GROUPS[group])
        return Response(OrderSerializer(orders, many=True, context={"request": request}).data)

    def post(self, request):
        serializer = PlaceOrderSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data
        try:
            order = place_order(
                lines=serializer.to_lines(),
                order_type=data["order_type"],
                table_number=data.get("table_number", ""),
                pickup_time=data.get("pickup_time"),
                payment_method=data["payment_method"],
                source=Order.Source.APP,
                client=request.user,
            )
        except OrderError as error:
            return _error(str(error))
        return Response(OrderSerializer(order, context={"request": request}).data, status=status.HTTP_201_CREATED)


class OrderDetailView(APIView):
    """Polled by the app (every ~15 s) to track status — proposal 4.1.10."""

    permission_classes = [IsClient]

    def get(self, request, pk):
        return Response(OrderSerializer(_own_order(request, pk), context={"request": request}).data)


class CancelOrderView(APIView):
    permission_classes = [IsClient]

    def post(self, request, pk):
        order = _own_order(request, pk)
        # Staff may cancel later steps from the dashboard; a client only while Pending.
        if order.status != Order.Status.PENDING:
            return _error(f"This order is already {order.get_status_display().lower()} and can't be cancelled.")
        try:
            order = cancel_order(order)
        except OrderError as error:
            return _error(str(error))
        return Response(OrderSerializer(order, context={"request": request}).data)


class PaymentProofView(APIView):
    """Design 40.1: the customer uploads a screenshot of their QR transfer for
    staff to check before they confirm the payment."""

    permission_classes = [IsClient]
    parser_classes = [MultiPartParser]

    def post(self, request, pk):
        order = _own_order(request, pk)
        if not order.can_confirm_payment:
            return _error("This order has no QR payment waiting for a screenshot.")
        serializer = PaymentProofSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        old_proof = order.payment_proof.name if order.payment_proof else None
        order.payment_proof = serializer.validated_data["image"]
        order.save(update_fields=["payment_proof", "updated_at"])
        if old_proof:
            # A re-upload replaces the old screenshot; don't leave it in MEDIA_ROOT.
            storage = order.payment_proof.storage
            transaction.on_commit(lambda: storage.delete(old_proof))
        return Response(OrderSerializer(order, context={"request": request}).data)
