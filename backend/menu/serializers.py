from rest_framework import serializers

from .models import Category, ItemOption, MenuItem


class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Category
        fields = ["id", "name", "display_order"]


class ItemOptionSerializer(serializers.ModelSerializer):
    class Meta:
        model = ItemOption
        fields = ["id", "name", "choices", "extra_price"]


class MenuItemSerializer(serializers.ModelSerializer):
    category = CategorySerializer(read_only=True)
    image_url = serializers.SerializerMethodField()
    options = ItemOptionSerializer(many=True, read_only=True)

    class Meta:
        model = MenuItem
        # `in_stock` (not the raw count) is what the app needs: an item at 0
        # is shown but can't be ordered (proposal 4.1.5).
        fields = ["id", "name", "description", "price", "image_url", "category", "in_stock", "options"]

    def get_image_url(self, obj):
        if not obj.image:
            return None
        request = self.context.get("request")
        return request.build_absolute_uri(obj.image.url) if request else obj.image.url
