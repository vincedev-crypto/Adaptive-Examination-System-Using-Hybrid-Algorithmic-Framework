document.addEventListener('DOMContentLoaded', function() {
    // This script is now only needed for older pages that still
    // use the upload type dropdown. On the new teacher dashboard
    // we always show the exam form directly.
    const uploadTypeDropdown = document.getElementById('uploadType');

    if (!uploadTypeDropdown) {
        return;
    }

    uploadTypeDropdown.addEventListener('change', function() {
        const selection = this.value;
        const form = document.getElementById('uploadForm');
        const examSection = document.getElementById('examSection');
        const resultsSection = document.getElementById('resultsSection');

        if (!form || !examSection || !resultsSection) {
            return;
        }

        if (selection === 'none') {
            form.classList.add('hidden');
        } else {
            form.classList.remove('hidden');

            // Toggle Exam Section (Fisher-Yates/IRT)
            if (selection === 'exam') {
                examSection.classList.remove('hidden');
            } else {
                examSection.classList.add('hidden');
            }

            // Toggle Results Section (Random Forest)
            if (selection === 'results') {
                resultsSection.classList.remove('hidden');
            } else {
                resultsSection.classList.add('hidden');
            }
        }
    });
});