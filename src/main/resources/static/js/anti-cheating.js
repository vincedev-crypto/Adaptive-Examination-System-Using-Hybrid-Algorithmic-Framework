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
        console.log('🚫 MAX VIOLATIONS REACHED - Auto-submitting exam');
        showViolationMaxModal();
        
        // Wait 3 seconds then submit
        setTimeout(() => {
            autoSubmitExam();
        }, 3000);
    }
}

/**
 * Show max violations modal
 */
function showViolationMaxModal() {
    const modal = document.createElement('div');
    modal.id = 'violationMaxModal';
    modal.innerHTML = `
        <div style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
                    background: rgba(0,0,0,0.95); z-index: 99999; display: flex; 
                    align-items: center; justify-content: center;">
            <div style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); 
                        padding: 60px; border-radius: 20px; text-align: center; 
                        max-width: 550px; box-shadow: 0 20px 60px rgba(0,0,0,0.5);
                        animation: shake 0.5s ease-in-out;">
                <div style="font-size: 80px; margin-bottom: 20px;">🚫</div>
                <h1 style="color: white; font-size: 42px; margin: 20px 0; 
                           font-weight: bold; text-shadow: 2px 2px 4px rgba(0,0,0,0.3);">
                    MAXIMUM VIOLATIONS REACHED!
                </h1>
                <p style="color: #fff; font-size: 20px; margin: 20px 0; line-height: 1.5;">
                    You have reached the maximum number of violations (${MAX_VIOLATIONS}).
                </p>
                <p style="color: #ffeb3b; font-size: 22px; font-weight: bold; margin: 20px 0;">
                    Your exam will be automatically submitted now.
                </p>
                <div style="margin-top: 30px;">
                    <div class="spinner-border text-light" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Submitting...</span>
                    </div>
                </div>
            </div>
        </div>
        <style>
            @keyframes shake {
                0%, 100% { transform: translateX(0); }
                25% { transform: translateX(-10px); }
                75% { transform: translateX(10px); }
            }
        </style>
    `;
    document.body.appendChild(modal);
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
