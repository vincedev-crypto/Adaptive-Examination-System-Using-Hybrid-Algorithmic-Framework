/* ============================================================
   teacher-view-students-list.js
   Accordion expand/collapse + live search for student cards
   ============================================================ */

document.addEventListener('DOMContentLoaded', function () {

    // ── Accordion toggle ──────────────────────────────────────
    document.querySelectorAll('.svl-card-header').forEach(function (header) {
        header.addEventListener('click', function () {
            const card    = header.closest('.svl-card');
            const panel   = card.querySelector('.svl-panel');
            const chevron = header.querySelector('.svl-chevron');

            const isOpen = panel.classList.contains('open');

            // Collapse all others
            document.querySelectorAll('.svl-panel.open').forEach(function (p) {
                p.classList.remove('open');
            });
            document.querySelectorAll('.svl-chevron.open').forEach(function (c) {
                c.classList.remove('open');
            });

            // Toggle current
            if (!isOpen) {
                panel.classList.add('open');
                chevron.classList.add('open');
            }
        });
    });

    // ── Live search ───────────────────────────────────────────
    const searchInput = document.getElementById('svlSearch');
    if (searchInput) {
        searchInput.addEventListener('input', function () {
            const term = searchInput.value.toLowerCase().trim();

            document.querySelectorAll('.svl-card').forEach(function (card) {
                const name  = (card.dataset.name  || '').toLowerCase();
                const email = (card.dataset.email || '').toLowerCase();
                const show  = !term || name.includes(term) || email.includes(term);
                card.style.display = show ? '' : 'none';
            });
        });
    }

});
