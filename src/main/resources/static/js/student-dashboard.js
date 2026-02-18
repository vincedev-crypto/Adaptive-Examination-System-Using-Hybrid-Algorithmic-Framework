/**
 * Google Classroom-Style Student Dashboard JavaScript
 * Handles interactions and animations for the student dashboard
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log('Student Dashboard loaded');
    
    // Add staggered animation to subject cards
    const subjectCards = document.querySelectorAll('.subject-card');
    subjectCards.forEach((card, index) => {
        card.style.animationDelay = `${index * 0.1}s`;
    });
    
    // Smooth scroll to top of activity list when clicking on pending activities
    const pendingActivities = document.querySelectorAll('.activity-pending');
    pendingActivities.forEach(activity => {
        activity.addEventListener('click', function() {
            this.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
    });
    
    // Add pulse animation to pending exam buttons
    const startExamButtons = document.querySelectorAll('.btn-success');
    startExamButtons.forEach(button => {
        setInterval(() => {
            button.style.transform = 'scale(1.05)';
            setTimeout(() => {
                button.style.transform = 'scale(1)';
            }, 200);
        }, 3000);
    });
    
    // Tooltip for activity types
    const badges = document.querySelectorAll('.badge');
    badges.forEach(badge => {
        badge.setAttribute('title', 'Activity Type: ' + badge.textContent);
    });
    
    // Confirmation before starting exam
    const examLinks = document.querySelectorAll('a[href*="take-exam"]');
    examLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            const activityName = this.closest('.activity-item').querySelector('h6').textContent;
            const confirmed = confirm(`Are you ready to start "${activityName}"?\n\nOnce you start, the timer will begin and cannot be paused.`);
            if (!confirmed) {
                e.preventDefault();
            }
        });
    });
    
    // Add loading spinner when navigating
    const allLinks = document.querySelectorAll('a[href^="/student/"]');
    allLinks.forEach(link => {
        link.addEventListener('click', function() {
            const spinner = document.createElement('div');
            spinner.className = 'spinner-border spinner-border-sm ms-2';
            spinner.setAttribute('role', 'status');
            this.appendChild(spinner);
        });
    });
    
    // Highlight urgent deadlines (within 24 hours)
    const now = new Date();
    const deadlines = document.querySelectorAll('.activity-item small.text-danger');
    deadlines.forEach(deadline => {
        const deadlineText = deadline.textContent;
        // Add blinking animation for urgent deadlines
        deadline.style.animation = 'blink 2s infinite';
    });
    
    // Auto-refresh activity count
    setInterval(() => {
        const activityCounts = document.querySelectorAll('.card-footer small');
        activityCounts.forEach(count => {
            count.style.transition = 'all 0.3s ease';
        });
    }, 5000);
    
    // Add keyboard shortcuts
    document.addEventListener('keydown', function(e) {
        // Press 'R' to reload
        if (e.key === 'r' && e.ctrlKey) {
            e.preventDefault();
            location.reload();
        }
    });
    
    // Observer for lazy loading images/content
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px 50px 0px'
    };
    
    const observer = new IntersectionObserver(function(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.style.opacity = '1';
            }
        });
    }, observerOptions);
    
    // Observe all activity items
    const activityItems = document.querySelectorAll('.activity-item');
    activityItems.forEach(item => {
        observer.observe(item);
    });
});

// Add blinking animation for urgent items
const style = document.createElement('style');
style.textContent = `
    @keyframes blink {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.5; }
    }
`;
document.head.appendChild(style);
