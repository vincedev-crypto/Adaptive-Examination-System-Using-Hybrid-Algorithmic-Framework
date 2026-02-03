# CSV Exam Format Guide

## Option 1: Full Exam CSV Format

Create a CSV file with the following columns:

```csv
Question,ChoiceA,ChoiceB,ChoiceC,ChoiceD,Answer
"What is the capital of France?","London","Berlin","Paris","Madrid","Paris"
"What is 2 + 2?","3","4","5","6","4"
"Which planet is closest to the Sun?","Venus","Mars","Mercury","Earth","Mercury"
```

### Column Descriptions:
- **Question**: The question text
- **ChoiceA**: First answer choice
- **ChoiceB**: Second answer choice
- **ChoiceC**: Third answer choice
- **ChoiceD**: Fourth answer choice
- **Answer**: The correct answer (must match one of the choices exactly)

### Important Notes:
- Use quotes around text that contains commas
- The Answer column should contain the actual answer text, not just the letter (A, B, C, or D)
- All columns are required
- First row is treated as header and will be skipped

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

### Sample Exam: sample_exam.csv
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
4. Upload your **CSV file** as the Test Questionnaire
5. (Optional) Upload a separate answer key CSV
6. Click **Process** to generate the exam

The system will:
- Parse your CSV file
- Shuffle question order using Fisher-Yates algorithm
- Shuffle answer choices for each question
- Store correct answers for automatic grading
- Make the exam available for distribution to students

---

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
