from rest_framework.generics import ListAPIView, RetrieveAPIView

from .models import Category, MenuItem
from .serializers import CategorySerializer, MenuItemSerializer

# Read-only menu for the app. Uses the default IsAuthenticated (proposal §5:
# every API requires authentication). Items the Owner switched off with
# "Show to customers" are left out entirely; items at zero stock stay listed
# with in_stock=false so the app can show them as Out of Stock.


def _visible_items():
    return MenuItem.objects.filter(is_available=True).select_related("category").prefetch_related("options")


class CategoryListView(ListAPIView):
    serializer_class = CategorySerializer
    queryset = Category.objects.all()


class MenuItemListView(ListAPIView):
    serializer_class = MenuItemSerializer

    def get_queryset(self):
        items = _visible_items().order_by("category__display_order", "category__name", "name")
        category = self.request.query_params.get("category", "")
        query = self.request.query_params.get("q", "").strip()
        if category.isdigit():
            items = items.filter(category_id=category)
        if query:
            items = items.filter(name__icontains=query)
        return items


class MenuItemDetailView(RetrieveAPIView):
    serializer_class = MenuItemSerializer

    def get_queryset(self):
        return _visible_items()
