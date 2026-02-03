# CSV Exam Format Guide

## Supported CSV Formats

The system supports **THREE flexible formats** for CSV exam uploads:

---

## Format 1: Full Format (Recommended)

Complete format with separate columns for everything:

```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD,Answer
"What is the capital of France?","London","Berlin","Paris","Madrid","Paris"
"What is 2 + 2?","3","4","5","6","4"
"Which planet is closest to the Sun?","Venus","Mars","Mercury","Earth","Mercury"
```

### Features:
- ✅ Most explicit and clear
- ✅ Easy to create in Excel/Google Sheets
- ✅ Separate answer column
- ✅ Best for team collaboration

---

## Format 2: Compact Format with Embedded Answers

Questions with embedded answers in the question text:

```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD
"What is the capital of France? Answer: Paris","London","Berlin","Paris","Madrid"
"What is 2 + 2? Answer: 4","3","4","5","6"
"Which planet is closest to the Sun? Answer: Mercury","Venus","Mars","Mercury","Earth"
```

### Features:
- ✅ Answer embedded in question text
- ✅ Automatic answer extraction
- ✅ Question text cleaned after parsing
- ✅ Alternative format: "Correct: [answer]"

---

## Format 3: Simple Single-Column Format

Minimal format with just question text containing embedded answers:

```csv
Question
"What is the capital of France? A) London B) Berlin C) Paris D) Madrid Answer: Paris"
"What is 2 + 2? A) 3 B) 4 C) 5 D) 6 Answer: 4"
"Which planet is closest to the Sun? Answer: Mercury"
```

### Features:
- ✅ Simplest format
- ✅ One column only
- ✅ Can include formatted choices or plain questions
- ✅ Automatic answer extraction

---

## Answer Extraction

The system automatically extracts answers from these patterns:
- `Answer: [correct answer]`
- `Correct: [correct answer]`
- Case-insensitive matching

Examples:
- "What is 2+2? Answer: 4" → Answer extracted: "4"
- "Capital of France? ANSWER: Paris" → Answer extracted: "Paris"
- "Best color? Correct: Blue" → Answer extracted: "Blue"
- **Question**: The question text
- **ChoiceA**: First answer choice
- **ChoiceB**: Second answer choice
- **ChoiceC**: Third answer choice
- **ChoiceD**: Fourth answer choice
- **Answer**: The correct answer (must match one of the choices exactly)

### Important Notes:
- Use quotes around text that contains commas
- The Answer can be in a separate column OR embedded in the question text
- All three formats are automatically detected
- First row can be a header (will be auto-detected and skipped)
- System extracts and removes "Answer: ..." from question text automatically

---

## Option 2: Separate Answer Key CSV

If you're uploading a separate answer key CSV, use one of these formats:

### Format A: Question Number with Answer
```csv
QuestionNumber,Answer
1,"Paris"
2,"4"
3,"Mercury"
```

### Format B: Just Answers (line by line)
```csv
Paris
4
Mercury
```

In Format B, the first line is answer to question 1, second line is answer to question 2, etc.

---

## Example CSV Files

### Sample 1: Full Format (Recommended)
```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD,Answer
"What is the chemical symbol for gold?","Au","Ag","Fe","Cu","Au"
"How many continents are there?","5","6","7","8","7"
```

### Sample 2: Embedded Answers
```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD
"What is the chemical symbol for gold? Answer: Au","Au","Ag","Fe","Cu"
"How many continents are there? Answer: 7","5","6","7","8"
```

### Sample 3: Simple Format
```csv
Question
"What is the chemical symbol for gold? A) Au B) Ag C) Fe D) Cu Answer: Au"
"How many continents are there? A) 5 B) 6 C) 7 D) 8 Answer: 7"
```

### Sample 4: Complete Example (Full Format)
```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD,Answer
"What is the chemical symbol for gold?","Au","Ag","Fe","Cu","Au"
"How many continents are there?","5","6","7","8","7"
"Who wrote 'Romeo and Juliet'?","Charles Dickens","William Shakespeare","Jane Austen","Mark Twain","William Shakespeare"
"What is the speed of light?","299,792 km/s","150,000 km/s","500,000 km/s","1,000,000 km/s","299,792 km/s"
"What year did World War II end?","1943","1944","1945","1946","1945"
```

### Sample Answer Key: sample_answers.csv
```csv
QuestionNumber,Answer
1,"Au"
2,"7"
3,"William Shakespeare"
4,"299,792 km/s"
5,"1945"
```

---

## Upload Instructions

1. **Teacher Dashboard** → Select "Prepare New Exam (Fisher-Yates)"
2. Enter **Subject Name** (e.g., "World History")
3. Select **Activity Type** (Exam, Quiz, Assignment, or Practice Test)
4. Upload your **CSV file** in any of the supported formats
5. (Optional) Upload a separate answer key CSV if using Format 2 without embedded answers
6. Click **Process** to generate the exam

The system will:
- **Auto-detect** which CSV format you're using
- **Extract answers** from embedded text if present
- Parse your CSV file with intelligent format detection
- Shuffle question order using Fisher-Yates algorithm
- Shuffle answer choices for each question
- Store correct answers for automatic grading
- Make the exam available for distribution to students

---

## Format Detection
**any of the three formats** - system auto-detects
- Embed answers in question text: "Question? Answer: correct"
- Use clear, unambiguous question text
- Test with a small CSV first
- Use quotes for text containing commas or special characters
- Mix formats if needed (system handles each row independently)

❌ **Don't:**
- Leave blank rows in the middle of your CSV
- Use different number of choices per question in Format 1
- Put just letters (A, B, C, D) in the Answer column
- Worry about header rows - system auto-detects them

---

## Quick Format Comparison

| Format | Columns | Answer Location | Best For |
|--------|---------|----------------|----------|
| **Format 1** | 6 | Separate column | Team collaboration, clarity |
| **Format 2** | 5 | Embedded in question | Compact files, existing questions |
| **Format 3** | 1 | Embedded in question | Quick imports, simple exports |

**All formats work equally well!** Choose what's easiest for your workflow.
## Tips

✅ **Do:**
- Use clear, unambiguous question text
- Ensure answers match exactly (case-sensitive)
- Test with a small CSV first
- Use quotes for text containing commas or special characters

❌ **Don't:**
- Leave blank rows in the middle of your CSV
- Use different number of choices per question
- Put just letters (A, B, C, D) in the Answer column
- Mix different CSV formats in one file
