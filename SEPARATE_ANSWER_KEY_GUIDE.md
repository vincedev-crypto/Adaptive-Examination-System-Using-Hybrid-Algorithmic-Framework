# Separate Answer Key PDF Feature

## Overview
The system now supports **two methods** for providing answer keys:

### Method 1: Single PDF with Embedded Answers (Original)
Upload one PDF file where each question includes an "Answer:" line indicating the correct answer.

**Example Format:**
```
1. What is the capital of France?
A) London
B) Paris
C) Berlin
D) Madrid
Answer: Paris

2. Who painted the Mona Lisa?
A) Michelangelo
B) Leonardo da Vinci
C) Raphael
D) Donatello
Answer: Leonardo da Vinci
```

### Method 2: Two Separate PDFs (New Feature)
Upload two files:
1. **Exam Questions PDF** - Contains only questions and answer choices
2. **Answer Key PDF** - Contains only the correct answers

**Example Answer Key PDF Formats Supported:**

#### Format A: Simple Numbered List
```
1. Paris
2. Leonardo da Vinci
3. Gold
4. 1945
5. Pacific Ocean
```

#### Format B: With Labels
```
Question 1: Paris
Question 2: Leonardo da Vinci
Question 3: Gold
Q4: 1945
Q5: Pacific Ocean
```

#### Format C: With Choice Letters
```
1. B) Paris
2. B) Leonardo da Vinci
3. C) Gold
4. D) 1945
5. A) Pacific Ocean
```

## How It Works

### Upload Process
1. Navigate to the **Teacher Homepage**
2. Scroll to the **"Process Exam"** section
3. You will see two file inputs:
   - **Exam PDF (Required)**: Upload your exam questions
   - **Answer Key PDF (Optional)**: Upload separate answer key if you have one

### Answer Key Merging Logic
- If you upload **only the exam PDF** with embedded "Answer:" lines, those answers will be used
- If you upload **both exam PDF and answer key PDF**, the system will:
  1. Extract embedded answers from the exam PDF (if any)
  2. Parse answers from the separate answer key PDF
  3. **Merge them together** (answer key PDF takes priority for duplicates)
  4. Use the combined answer key for grading

### Parsing Intelligence
The `parseAnswerKeyPdf()` method automatically detects and handles:
- **Simple numbered answers**: "1. Mars" → Extracts "Mars" for question 1
- **Labeled questions**: "Question 5: Jupiter" or "Q5: Jupiter" → Extracts "Jupiter" for question 5
- **Choice-prefixed answers**: "3. C) Gold" → Extracts "Gold" for question 3
- **Sequential line-by-line**: First non-header line = Q1, second = Q2, etc.
- **Headers are ignored**: Lines containing "Answer Key", "Answer Sheet", "Answers" are automatically skipped

## Technical Implementation

### Modified Files
1. **homepage.html**
   - Added `<input name="answerKeyPdf" type="file" accept=".pdf">` (optional)
   - Added explanatory text showing both upload options

2. **HomepageController.java**
   - Updated `processExams()` to accept `@RequestParam(value = "answerKeyPdf", required = false)`
   - Added `parseAnswerKeyPdf(MultipartFile file)` method
   - Updated `processFisherYates()` to accept external answer key parameter
   - Implemented answer key merging logic with `answerKey.putAll(externalAnswerKey)`

### Code Flow
```
User uploads exam PDF + answer key PDF
         ↓
processExams() method receives both files
         ↓
If answerKeyPdf exists → parseAnswerKeyPdf() extracts answers
         ↓
processFisherYates() extracts embedded answers (if any)
         ↓
Merge: externalAnswerKey overrides embedded answers
         ↓
Store combined answer key in session
         ↓
When exam distributed → stored in AnswerKeyService per student
         ↓
Student submits → gradeExam() uses stored answer key
```

## Usage Scenarios

### Scenario 1: Traditional Single PDF
- **Upload**: 1 PDF with questions and "Answer:" lines
- **Result**: System extracts answers automatically
- **Use Case**: When you create exams in Word/PDF with embedded answers

### Scenario 2: Separate PDFs
- **Upload**: Questions PDF + Answer Key PDF
- **Result**: System parses answer key PDF and uses it for grading
- **Use Case**: When you have a question bank and separate answer sheet

### Scenario 3: Hybrid (Both Methods)
- **Upload**: Questions PDF with some "Answer:" lines + Answer Key PDF
- **Result**: System merges both, prioritizing the separate answer key
- **Use Case**: Backup or override mechanism for complex exams

## Benefits
✅ **Flexibility**: Teachers can use their existing document formats  
✅ **Compatibility**: Supports various answer key styles from different sources  
✅ **No Reformatting**: No need to manually add "Answer:" lines to PDFs  
✅ **Error Reduction**: Separate answer keys reduce copy-paste errors  
✅ **Standard Formats**: Works with common test bank answer key formats  

## Notes
- The answer key PDF is **optional** - you can still use single PDF method
- Both PDFs must be valid PDF files
- Question numbers in answer key must match question numbers in exam
- The system is case-insensitive for answer matching
- Answers are automatically trimmed of whitespace
- If duplicate question numbers exist, separate answer key takes priority

## Future Enhancements
- Support for Excel answer key files
- Support for CSV format
- Multiple choice letter-only answer keys (e.g., "1. B\n2. C\n3. A")
- Preview answer key before distribution
