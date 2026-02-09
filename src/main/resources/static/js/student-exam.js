/**
 * Student Exam Page JavaScript
 * Handles exam navigation, timer, auto-save, and anti-cheating features
 */

// Global variables (will be initialized from Thymeleaf)
let exam = [];
let difficulties = [];
let examInfo = {};
let totalQuestions = 0;
let currentPage = 0;
let answers = {};
let timerInterval;
let timeRemainingSeconds;

// Anti-cheating variables
let violationCount = 0;
const MAX_VIOLATIONS = 5;
let tabSwitchCount = 0;
let isExamActive = true;

/**
 * Initialize the exam from Thymeleaf data
 */
function initializeExam(examData, difficultiesData, examInfoData) {
    exam = examData;
    difficulties = difficultiesData;
    examInfo = examInfoData;
    totalQuestions = exam.length;
    
    loadSavedAnswers();
    displayQuestion();
    initializeTimer();
    displayDeadline();
    initializeAntiCheating();
}

/**
 * Setup event listeners when DOM is ready
 */
function setupEventListeners() {
    // Navigation button event listeners
    document.getElementById('backBtn').addEventListener('click', navigateBack);
    document.getElementById('nextBtn').addEventListener('click', navigateNext);
}

/**
 * Main initialization function called from HTML
 */
window.startExam = function(examData, difficultiesData, examInfoData) {
    // Wait for DOM to be ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initializeExam(examData, difficultiesData, examInfoData);
            setupEventListeners();
        });
    } else {
        // DOM already loaded
        initializeExam(examData, difficultiesData, examInfoData);
        setupEventListeners();
    }
};

/**
 * Load saved answers from localStorage
 */
function loadSavedAnswers() {
    const saved = localStorage.getItem('examAnswers');
    if (saved) {
        answers = JSON.parse(saved);
    }
}

/**
 * Auto-save answers to localStorage
 */
function autoSave() {
    localStorage.setItem('examAnswers', JSON.stringify(answers));
    showSaveIndicator();
}

/**
 * Show auto-save indicator
 */
function showSaveIndicator() {
    const indicator = document.getElementById('autoSaveIndicator');
    indicator.style.display = 'block';
    setTimeout(() => {
        indicator.style.display = 'none';
    }, 1500);
}

/**
 * Display current question
 */
function displayQuestion() {
    let question = exam[currentPage];
    const questionNumber = currentPage + 1;
    
    // Remove [TEXT_INPUT] prefix if present
    const isTextInput = question.includes('[TEXT_INPUT]');
    if (isTextInput) {
        question = question.replace('[TEXT_INPUT]', '').trim();
    }
    
    // Remove difficulty markers like [Easy], [Medium], [Hard], [Essay], [Open-Ended]
    question = question.replace(/\[(Easy|Medium|Hard|Essay|Open-Ended|Open Ended|TEXT_INPUT)\]/gi, '').trim();
    
    let html = `<h5 class="mb-4">Question ${questionNumber}</h5>`;
    
    if (isTextInput) {
        // Text-input question (prefix hidden from student)
        html += `
            <p class="lead mb-4">${question}</p>
            <div class="form-group">
                <label class="form-label fw-bold">Your Answer:</label>
                <textarea class="form-control" rows="4" 
                          id="textAnswer${questionNumber}"
                          placeholder="Type your answer here..."
                          onblur="saveTextAnswer(${questionNumber}, this.value)">${answers['q' + questionNumber] || ''}</textarea>
                <small class="form-text text-muted">This is an open-ended question. Provide a detailed answer.</small>
            </div>
        `;
    } else {
        // Multiple-choice question
        const lines = question.split('\n');
        const questionText = lines[0];
        const choices = lines.slice(1).filter(line => line.trim());
        
        html += `
            <p class="lead mb-4">${questionText}</p>
            <div class="choices">
        `;
        
        choices.forEach((choice, idx) => {
            const choiceLetter = choice.split(')')[0].trim();
            const choiceText = choice.substring(choice.indexOf(')') + 1).trim();
            const isSelected = answers['q' + questionNumber] === choiceText;
            
            html += `
                <button type="button" class="btn choice-btn w-100 ${isSelected ? 'selected' : ''}" 
                        onclick="selectAnswer(${questionNumber}, '${choiceText.replace(/'/g, "\\'")}', this)">
                    <strong>${choiceLetter})</strong> ${choiceText}
                </button>
            `;
        });
        
        html += '</div>';
    }
    
    document.getElementById('questionContainer').innerHTML = html;
    
    // Update UI elements
    updateProgress();
    
    // Update navigation buttons
    document.getElementById('backBtn').disabled = (currentPage === 0);
    document.getElementById('nextBtn').textContent = (currentPage === totalQuestions - 1) ? 'Submit' : 'Next →';
}

/**
 * Save text answer for open-ended questions
 */
function saveTextAnswer(questionNumber, value) {
    if (value && value.trim()) {
        answers['q' + questionNumber] = value.trim();
    } else {
        delete answers['q' + questionNumber];
    }
    autoSave();
}

/**
 * Select an answer for multiple-choice question
 */
function selectAnswer(questionNum, answer, button) {
    // Remove selected class from all choices
    document.querySelectorAll('.choice-btn').forEach(btn => btn.classList.remove('selected'));
    
    // Add selected class to clicked choice
    button.classList.add('selected');
    
    // Save answer
    answers['q' + questionNum] = answer;
    autoSave();
}

/**
 * Update progress bar and indicators
 */
function updateProgress() {
    const questionNum = currentPage + 1;
    const percentage = (questionNum / totalQuestions) * 100;
    
    document.getElementById('progressText').textContent = `Question ${questionNum} of ${totalQuestions}`;
    document.getElementById('progressBar').style.width = percentage + '%';
    document.getElementById('questionNumber').textContent = `${questionNum} / ${totalQuestions}`;
}

/**
 * Navigate to previous question
 */
function navigateBack() {
    if (currentPage > 0) {
        currentPage--;
        displayQuestion();
    }
}

/**
 * Navigate to next question or submit
 */
function navigateNext() {
    if (currentPage < totalQuestions - 1) {
        currentPage++;
        displayQuestion();
    } else {
        submitExam();
    }
}

/**
 * Submit the exam
 */
function submitExam() {
    if (confirm('Are you sure you want to submit your exam? You cannot change your answers after submission.')) {
        // Clear timer
        if (timerInterval) {
            clearInterval(timerInterval);
        }
        
        // Create form and submit
        const form = document.getElementById('examForm');
        form.innerHTML = '';
        
        for (const [key, value] of Object.entries(answers)) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = value;
            form.appendChild(input);
        }
        
        // Clear localStorage
        localStorage.removeItem('examAnswers');
        
        form.submit();
    }
}

/**
 * Initialize timer
 */
function initializeTimer() {
    const timeLimitMinutes = parseInt(examInfo.timeLimit) || 60;
    const startTimeMillis = parseInt(examInfo.startTimeMillis);
    
    console.log('🕒 Initializing timer - Time limit:', timeLimitMinutes, 'minutes');
    console.log('🕒 Start time (epoch ms):', startTimeMillis);
    
    // Calculate remaining time using epoch milliseconds
    if (!startTimeMillis || isNaN(startTimeMillis)) {
        console.warn('⚠️ No valid start time provided, using full time limit');
        timeRemainingSeconds = timeLimitMinutes * 60;
    } else {
        const nowMillis = Date.now();
        const elapsedSeconds = Math.floor((nowMillis - startTimeMillis) / 1000);
        timeRemainingSeconds = (timeLimitMinutes * 60) - elapsedSeconds;
        
        console.log('🕒 Current time (epoch ms):', nowMillis);
        console.log('🕒 Elapsed:', elapsedSeconds, 'seconds');
        console.log('🕒 Remaining:', timeRemainingSeconds, 'seconds');
        
        // Minimum 5 seconds to prevent immediate auto-submit
        if (timeRemainingSeconds < 5) {
            console.warn('⚠️ Timer shows < 5 seconds. Check if this is correct!');
            if (elapsedSeconds < 5) {
                console.log('✅ Exam just started, using full time limit');
                timeRemainingSeconds = timeLimitMinutes * 60;
            } else {
                timeRemainingSeconds = 0;
            }
        }
    }
    
    console.log('✅ Timer initialized with', timeRemainingSeconds, 'seconds remaining');
    
    updateTimerDisplay();
    timerInterval = setInterval(() => {
        timeRemainingSeconds--;
        updateTimerDisplay();
        
        if (timeRemainingSeconds <= 0) {
            clearInterval(timerInterval);
            autoSubmitExam();
        }
    }, 1000);
}

/**
 * Update timer display
 */
function updateTimerDisplay() {
    const hours = Math.floor(timeRemainingSeconds / 3600);
    const minutes = Math.floor((timeRemainingSeconds % 3600) / 60);
    const seconds = timeRemainingSeconds % 60;
    
    const timerElement = document.getElementById('timerDisplay');
    const formattedTime = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    timerElement.textContent = `Time: ${formattedTime}`;
    
    // Change color based on remaining time
    if (timeRemainingSeconds <= 60) {
        timerElement.className = 'badge bg-danger fs-6 timer-warning';
    } else if (timeRemainingSeconds <= 300) {
        timerElement.className = 'badge bg-warning text-dark fs-6 timer-warning';
    } else {
        timerElement.className = 'badge bg-success fs-6';
    }
}

/**
 * Display deadline
 */
function displayDeadline() {
    const deadline = examInfo.deadline;
    if (deadline) {
        const deadlineDate = new Date(deadline);
        const options = { 
            month: 'short', 
            day: 'numeric', 
            year: 'numeric',
            hour: '2-digit', 
            minute: '2-digit'
        };
        document.getElementById('deadlineDisplay').textContent = deadlineDate.toLocaleString('en-US', options);
        
        // Check deadline every minute
        setInterval(() => {
            checkDeadline(deadlineDate);
        }, 60000); // Check every 60 seconds
        
        // Also check immediately
        checkDeadline(deadlineDate);
    }
}

/**
 * Check if deadline has passed and auto-submit if needed
 */
function checkDeadline(deadlineDate) {
    const now = new Date();
    if (now > deadlineDate) {
        console.log('🚫 Deadline exceeded - auto-submitting exam');
        alert('The exam deadline has passed. Your exam will be automatically submitted.');
        autoSubmitExam();
    }
}

/**
 * Auto-submit exam when time expires
 */
function autoSubmitExam() {
    // Show time's up modal
    showTimesUpModal();
    
    // Wait 3 seconds then submit
    setTimeout(() => {
        console.log('⏰ Time expired - force submitting exam');
        
        // Create form and submit without confirmation
        const form = document.getElementById('examForm');
        form.innerHTML = '';
        
        for (const [key, value] of Object.entries(answers)) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = value;
            form.appendChild(input);
        }
        
        form.submit();
    }, 3000);
}

/**
 * Show Time's Up modal
 */
function showTimesUpModal() {
    const modal = document.createElement('div');
    modal.id = 'timesUpModal';
    modal.innerHTML = `
        <div style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
                    background: rgba(0,0,0,0.9); z-index: 99999; display: flex; 
                    align-items: center; justify-content: center;">
            <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); 
                        padding: 60px; border-radius: 20px; text-align: center; 
                        max-width: 500px; box-shadow: 0 20px 60px rgba(0,0,0,0.5);
                        animation: slideIn 0.5s ease-out;">
                <div style="font-size: 80px; margin-bottom: 20px;">⏰</div>
                <h1 style="color: white; font-size: 48px; margin: 20px 0; 
                           font-weight: bold; text-shadow: 2px 2px 4px rgba(0,0,0,0.3);">
                    TIME'S UP!
                </h1>
                <p style="color: #f0f0f0; font-size: 20px; margin: 20px 0;">
                    Your exam time has expired.
                </p>
                <p style="color: #ffd700; font-size: 18px; font-weight: bold;">
                    Submitting your answers automatically...
                </p>
                <div style="margin-top: 30px;">
                    <div class="spinner-border text-light" role="status" style="width: 3rem; height: 3rem;">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                </div>
            </div>
        </div>
        <style>
            @keyframes slideIn {
                from {
                    transform: translateY(-100px);
                    opacity: 0;
                }
                to {
                    transform: translateY(0);
                    opacity: 1;
                }
            }
        </style>
    `;
    document.body.appendChild(modal);
}

/**
 * Prevent accidental page leave
 */
window.addEventListener('beforeunload', (e) => {
    if (Object.keys(answers).length > 0 && timeRemainingSeconds > 0) {
        e.preventDefault();
        e.returnValue = '';
    }
});
