from decimal import Decimal

from rest_framework import serializers

from menu.models import ItemOption

from .models import Order, OrderItem

MAX_PROOF_BYTES = 2 * 1024 * 1024


class OrderItemSerializer(serializers.ModelSerializer):
    line_total = serializers.DecimalField(max_digits=10, decimal_places=2, read_only=True)
    image_url = serializers.SerializerMethodField()

    class Meta:
        model = OrderItem
        fields = ["name", "options", "unit_price", "quantity", "line_total", "image_url"]

    def get_image_url(self, obj):
        # The menu item may have been deleted since (menu_item is SET_NULL);
        # the order keeps its name and price snapshot either way.
        if not obj.menu_item or not obj.menu_item.image:
            return None
        request = self.context.get("request")
        url = obj.menu_item.image.url
        return request.build_absolute_uri(url) if request else url


class OrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)
    status_label = serializers.CharField(source="get_status_display")
    order_type_label = serializers.CharField(source="get_order_type_display")
    payment_method_label = serializers.CharField(source="get_payment_method_display")
    payment_proof_url = serializers.SerializerMethodField()
    can_cancel = serializers.SerializerMethodField()
    can_upload_proof = serializers.SerializerMethodField()

    class Meta:
        model = Order
        fields = [
            "id", "number", "status", "status_label", "order_type", "order_type_label",
            "table_number", "pickup_time", "payment_method", "payment_method_label",
            "payment_status", "payment_status_label", "payment_proof_url", "total",
            "rejection_reason", "items", "can_cancel", "can_upload_proof", "created_at", "updated_at",
        ]

    def get_payment_proof_url(self, obj):
        if not obj.payment_proof:
            return None
        request = self.context.get("request")
        return request.build_absolute_uri(obj.payment_proof.url) if request else obj.payment_proof.url

    def get_can_cancel(self, obj):
        # Proposal 4.1.6: a client may cancel only while the order is Pending.
        return obj.status == Order.Status.PENDING

    def get_can_upload_proof(self, obj):
        return obj.can_confirm_payment


class OptionChoiceSerializer(serializers.Serializer):
    option = serializers.IntegerField()
    choice = serializers.CharField(max_length=50, required=False, allow_blank=True)


class OrderLineSerializer(serializers.Serializer):
    menu_item = serializers.IntegerField()
    quantity = serializers.IntegerField(min_value=1, max_value=50)
    options = OptionChoiceSerializer(many=True, required=False)


class PlaceOrderSerializer(serializers.Serializer):
    order_type = serializers.ChoiceField(choices=Order.OrderType.choices)
    table_number = serializers.CharField(max_length=20, required=False, allow_blank=True)
    pickup_time = serializers.TimeField(required=False, allow_null=True)
    payment_method = serializers.ChoiceField(choices=Order.PaymentMethod.choices)
    items = OrderLineSerializer(many=True, allow_empty=False, max_length=30)

    def validate(self, attrs):
        # Proposal 4.1.6 / acceptance criteria: each order type needs its detail.
        if attrs["order_type"] == Order.OrderType.DINE_IN and not attrs.get("table_number", "").strip():
            raise serializers.ValidationError({"table_number": "Table number is required for dine-in orders."})
        if attrs["order_type"] == Order.OrderType.PICKUP and not attrs.get("pickup_time"):
            raise serializers.ValidationError({"pickup_time": "Pickup time is required for pickup orders."})
        return attrs

    def to_lines(self):
        """Service-layer lines: (menu_item_id, quantity, options label, extra price).

        Prices always come from the database — the app only says which option
        and choice was picked, never what it costs."""
        requested = {
            pick["option"]
            for line in self.validated_data["items"]
            for pick in line.get("options", [])
        }
        options = ItemOption.objects.in_bulk(requested)

        lines = []
        for line in self.validated_data["items"]:
            labels, extra, seen = [], Decimal("0"), set()
            for pick in line.get("options", []):
                option = options.get(pick["option"])
                if option is None or option.menu_item_id != line["menu_item"]:
                    raise serializers.ValidationError({"items": "An option doesn't belong to its item."})
                if option.pk in seen:
                    raise serializers.ValidationError({"items": f"{option.name} was chosen twice."})
                seen.add(option.pk)
                choice = pick.get("choice", "")
                if option.choices:
                    if choice not in option.choices:
                        raise serializers.ValidationError(
                            {"items": f"Choose one of {', '.join(option.choices)} for {option.name}."}
                        )
                    labels.append(f"{option.name}: {choice}")
                else:
                    labels.append(option.name)
                extra += option.extra_price
            lines.append((line["menu_item"], line["quantity"], " · ".join(labels), extra))
        return lines


class PaymentProofSerializer(serializers.Serializer):
    image = serializers.ImageField()

    def validate_image(self, image):
        # Same limits as menu images in the dashboard.
        if image.size > MAX_PROOF_BYTES:
            raise serializers.ValidationError("Image must be 2 MB or smaller.")
        if getattr(image, "image", None) is None or image.image.format not in {"PNG", "JPEG"}:
            raise serializers.ValidationError("Image must be a PNG or JPG file.")
        return image
