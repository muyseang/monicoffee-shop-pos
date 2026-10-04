from django.contrib import admin

from .models import Category, ItemOption, MenuItem

admin.site.register(Category)


class ItemOptionInline(admin.TabularInline):
    model = ItemOption
    extra = 0


@admin.register(MenuItem)
class MenuItemAdmin(admin.ModelAdmin):
    list_display = ("name", "category", "price", "stock", "is_available")
    list_filter = ("category", "is_available")
    search_fields = ("name",)
    inlines = [ItemOptionInline]
