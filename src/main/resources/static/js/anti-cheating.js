/**
 * Anti-Cheating Features for Student Exam
 * Prevents tab switching, screenshots, right-click, and other cheating attempts
 */

/**
 * Initialize all anti-cheating features
 */
function initializeAntiCheating() {
    // Track tab switching / window focus
    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    // Detect window blur (Alt+Tab)
    window.addEventListener('blur', handleWindowBlur);
    
    // Disable right-click
    document.addEventListener('contextmenu', handleContextMenu);
    
    // Disable keyboard shortcuts
    document.addEventListener('keydown', handleKeyboardShortcuts);
    
    // Disable copy/cut
    document.addEventListener('copy', handleCopy);
    document.addEventListener('cut', handleCut);
    
    // Detect fullscreen exit
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    
    console.log('✅ Anti-cheating features enabled');
    console.log('⚠️ Violations will be tracked. Maximum allowed: ' + MAX_VIOLATIONS);
}

/**
 * Handle visibility change (tab switching)
 */
function handleVisibilityChange() {
    if (document.hidden && isExamActive) {
        tabSwitchCount++;
        logViolation('Tab switching detected');
        showWarningModal('You switched tabs or minimized the window!');
    }
}

/**
 * Handle window blur (Alt+Tab)
 */
function handleWindowBlur() {
    if (isExamActive) {
        logViolation('Window lost focus (Alt+Tab detected)');
        showWarningModal('Please keep the exam window active!');
    }
}

/**
 * Handle right-click (context menu)
 */
function handleContextMenu(e) {
    e.preventDefault();
    logViolation('Right-click attempted');
    showWarningModal('Right-click is disabled during the exam!');
    return false;
}

/**
 * Handle keyboard shortcuts for screenshots and dev tools
 */
function handleKeyboardShortcuts(e) {
    // Print Screen
    if (e.key === 'PrintScreen') {
        e.preventDefault();
        logViolation('Screenshot attempt (Print Screen)');
        showWarningModal('Screenshots are not allowed during the exam!');
        return false;
    }
    
    // F12 (Developer Tools)
    if (e.key === 'F12') {
        e.preventDefault();
        logViolation('F12 (Developer Tools) attempted');
        showWarningModal('Developer tools are disabled during the exam!');
        return false;
    }
    
    // Ctrl+Shift+I (Developer Tools)
    if (e.ctrlKey && e.shiftKey && e.key === 'I') {
        e.preventDefault();
        logViolation('Ctrl+Shift+I (Developer Tools) attempted');
        showWarningModal('Developer tools are disabled during the exam!');
        return false;
    }
    
    // Ctrl+Shift+J (Console)
    if (e.ctrlKey && e.shiftKey && e.key === 'J') {
        e.preventDefault();
        logViolation('Ctrl+Shift+J (Console) attempted');
        showWarningModal('Developer console is disabled during the exam!');
        return false;
    }
    
    // Ctrl+Shift+C (Inspect Element)
    if (e.ctrlKey && e.shiftKey && e.key === 'C') {
        e.preventDefault();
        logViolation('Ctrl+Shift+C (Inspect) attempted');
        showWarningModal('Inspect element is disabled during the exam!');
        return false;
    }
    
    // Ctrl+U (View Source)
    if (e.ctrlKey && e.key === 'u') {
        e.preventDefault();
        logViolation('Ctrl+U (View Source) attempted');
        showWarningModal('Viewing source is disabled during the exam!');
        return false;
    }
    
    // Ctrl+S (Save page)
    if (e.ctrlKey && e.key === 's') {
        e.preventDefault();
        logViolation('Ctrl+S (Save page) attempted');
        showWarningModal('Saving the page is not allowed during the exam!');
        return false;
    }
    
    // Ctrl+P (Print)
    if (e.ctrlKey && e.key === 'p') {
        e.preventDefault();
        logViolation('Ctrl+P (Print) attempted');
        showWarningModal('Printing is not allowed during the exam!');
        return false;
    }
}

/**
 * Handle copy attempt
 */
function handleCopy(e) {
    e.preventDefault();
    logViolation('Copy attempt (Ctrl+C)');
    showWarningModal('Copying text is not allowed during the exam!');
    return false;
}

/**
 * Handle cut attempt
 */
function handleCut(e) {
    e.preventDefault();
    return false;
}

/**
 * Handle fullscreen change
 */
function handleFullscreenChange() {
    if (!document.fullscreenElement && isExamActive) {
        logViolation('Exited fullscreen mode');
        showWarningModal('Please stay in fullscreen mode!');
    }
}

/**
 * Log violation and update counter
 */
function logViolation(type) {
    violationCount++;
    console.warn('🚨 VIOLATION #' + violationCount + ': ' + type);
    
    // Update violation counter display
    const counter = document.getElementById('violationCounter');
    const countSpan = document.getElementById('violationCount');
    countSpan.textContent = violationCount;
    counter.style.display = 'block';
    
    // Send violation to server (optional - for logging)
    // You can implement server-side logging here if needed
    
    // Auto-submit after max violations
    if (violationCount >= MAX_VIOLATIONS) {
        isExamActive = false;
        alert('Maximum violations reached (' + MAX_VIOLATIONS + '). Your exam will be submitted now.');
        autoSubmitExam();
    }
}

/**
 * Show cheating warning modal
 */
function showWarningModal(message) {
    const modal = document.getElementById('cheatingModal');
    const messageEl = document.getElementById('cheatingMessage');
    const countEl = document.getElementById('modalViolationCount');
    
    messageEl.textContent = message;
    countEl.textContent = violationCount;
    modal.style.display = 'flex';
    
    // Auto-close after 5 seconds
    setTimeout(() => {
        closeWarningModal();
    }, 5000);
}

/**
 * Close warning modal
 */
function closeWarningModal() {
    const modal = document.getElementById('cheatingModal');
    modal.style.display = 'none';
}

/**
 * Request fullscreen mode (optional)
 */
function requestFullscreen() {
    const elem = document.documentElement;
    if (elem.requestFullscreen) {
        elem.requestFullscreen().catch(err => {
            console.log('Fullscreen request failed:', err);
        });
    }
}

// Optional: Uncomment to enable fullscreen mode on exam start
// setTimeout(() => requestFullscreen(), 1000);
