import os

from django import template
from django.contrib.staticfiles import finders
from django.templatetags.static import static

register = template.Library()


@register.simple_tag
def versioned_static(path):
    """The static URL with the file's modification time appended (?v=…), so a browser
    fetches the stylesheet or script again after it changes instead of reusing an old copy."""
    url = static(path)
    found = finders.find(path)
    return f"{url}?v={int(os.path.getmtime(found))}" if found else url
