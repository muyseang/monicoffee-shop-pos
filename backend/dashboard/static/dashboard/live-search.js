// Filters table rows as the user types. Markup contract:
//   <input data-live-search data-server-query="{{ query }}">
//   <tr data-search-name="lowercased name"> ... one per row
//   <tr id="no-search-results" hidden> ... shown when nothing matches
// The server-side ?q= search (pressing Enter) still works and keeps the term in the URL.
document.querySelectorAll('input[data-live-search]').forEach((input) => {
  const rows = document.querySelectorAll('tr[data-search-name]');
  const emptyRow = document.getElementById('no-search-results');

  input.addEventListener('input', () => {
    const term = input.value.trim().toLowerCase();
    // Rows from an earlier server search are already narrowed down, so
    // clearing the box has to reload to bring the other rows back.
    if (term === '' && input.dataset.serverQuery) {
      input.form.submit();
      return;
    }
    let visible = 0;
    rows.forEach((row) => {
      row.hidden = !row.dataset.searchName.includes(term);
      if (!row.hidden) visible += 1;
    });
    emptyRow.hidden = visible > 0 || rows.length === 0;
  });
});
