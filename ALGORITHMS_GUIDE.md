# Algorithm Implementation Guide

## Overview
This system implements three distinct algorithms for adaptive examination:

1. **IRT 3PL (Item Response Theory - Three-Parameter Logistic Model)**
2. **Fisher-Yates Shuffle Algorithm**
3. **Random Forest Analytics**

---

## 1. IRT 3PL (Three-Parameter Logistic Model)

### Location
`src/main/java/com/exam/service/IRT3PLService.java`

### Purpose
Estimates student ability level (θ) based on their response patterns, accounting for:
- Item discrimination (how well questions differentiate abilities)
- Item difficulty (ability level required for 50% success probability)
- Guessing probability (chance of correct answer by random guessing)

### Mathematical Model
```
P(θ) = c + (1 - c) / (1 + e^(-a(θ - b)))
```

Where:
- **θ (theta)**: Student ability (-4 to +4, where 0 is average)
- **a**: Discrimination parameter (0.5 to 2.5, higher = better differentiation)
- **b**: Difficulty parameter (-3 to +3, higher = more difficult)
- **c**: Guessing parameter (0.15 to 0.25 for multiple choice)

### Key Features
- **Maximum Likelihood Estimation (MLE)**: Uses Newton-Raphson method to estimate ability
- **Fisher Information**: Calculates measurement precision
- **Standard Error**: Provides confidence interval for ability estimate
- **Scaled Scores**: Converts theta to interpretable scores (e.g., 200-800 scale)
- **Adaptive Item Selection**: Selects next best question based on current ability estimate

### Methods

#### `calculateProbability(theta, params)`
Calculates probability of correct response given ability and item parameters.

#### `estimateAbility(responses, itemParams)`
Estimates student ability using Newton-Raphson iteration.
- **Input**: List of true/false responses, item parameters
- **Output**: AbilityEstimate object with theta, standard error, and statistics

#### `generateDefaultItemParameters(numQuestions)`
Generates realistic default parameters for questions (used when calibrated parameters aren't available).

#### `calibrateItems(allResponses)`
Simplified item calibration based on student response patterns.

#### `selectNextItem(currentTheta, availableItems, usedIndices)`
Selects the next best item for adaptive testing (maximizes information).

#### `thetaToScaledScore(theta, mean, sd)`
Converts theta to scaled score (e.g., SAT-style 500 ± 100).

### Integration
- Called in `StudentController.submitExam()` after grading
- Results stored in session: `irtTheta`, `irtScaledScore`, `irtStandardError`
- Displayed in `student-results.html`

### Example Output
```
=== IRT 3PL Analysis ===
Estimated Ability (θ): 0.523
Standard Error: 0.412
Scaled Score (500±100): 552
========================
```

---

## 2. Fisher-Yates Shuffle Algorithm

### Location
`src/main/java/com/exam/service/FisherYatesService.java`

### Purpose
Randomizes answer choices to prevent cheating while maintaining answer key integrity.

### Algorithm
Classic Fisher-Yates (Knuth) shuffle with O(n) time complexity:
```
for i = n-1 down to 1:
    j = random integer with 0 ≤ j ≤ i
    swap array[i] with array[j]
```

### Key Features
- **Unbiased Randomization**: Every permutation has equal probability
- **Efficient**: O(n) time, O(1) space
- **Cryptographically Secure**: Uses `SecureRandom` for unpredictable shuffling
- **Answer Key Preservation**: Correct answers tracked independently of choice order

### Methods

#### `shuffle(list)`
Shuffles any list using Fisher-Yates algorithm.

#### `shuffleQuestion(question)`
Shuffles choices for a single question while preserving correct answer.

#### `shuffleQuestions(questions)`
Shuffles choices for multiple questions.

#### `createRandomizedExam(questions)`
Creates formatted exam with shuffled choices.

#### `generateAnswerKey(questions)`
Generates answer key map from questions.

#### `testShuffleDistribution(items, iterations)`
Tests shuffle fairness (for validation).

### Integration
- Used in `HomepageController.extractAnswerAndShuffle()`
- Replaces `Collections.shuffle()` with proper Fisher-Yates implementation
- Each student receives uniquely shuffled answer choices

### Example
```java
// Original question
A) Paris
B) London
C) Berlin
D) Rome

// After Fisher-Yates shuffle (example)
A) Berlin
B) Paris  ← Correct answer tracked
C) Rome
D) London
```

---

## 3. Random Forest Analytics

### Location
`src/main/java/Service/RandomForestService.java`

### Purpose
Provides comprehensive performance analytics using Random Forest-inspired metrics.

### Metrics

#### **Topic Mastery** (0-100%)
Analyzes performance across different topic areas.
- Divides questions into topics
- Calculates accuracy per topic
- Returns average mastery score

#### **Difficulty Resilience** (0-100%)
Measures ability to handle difficult questions.
- Compares performance on easy vs. hard questions
- Higher score = better handling of challenging items

#### **Accuracy** (0-100%)
Overall correctness percentage.
- Simple ratio: correct answers / total questions

#### **Time Efficiency** (0-100%)
Normalized time performance.
- Optimal time: 45 seconds per question
- Penalizes too fast (rushing) or too slow (struggling)

#### **Confidence** (0-100%)
Based on answer consistency and completion rate.
- Weighted combination of completion and accuracy
- Formula: `(completion * 0.3) + (accuracy * 0.7)`

### Classification
Uses ensemble decision-making to categorize performance:
- **Excellent**: 80-100%
- **Good**: 60-79%
- **Fair**: 40-59%
- **Needs Improvement**: 0-39%

### Methods

#### `calculateStudentAnalytics(studentId, answers, answerKey, timeTaken)`
Main analysis method returning comprehensive StudentAnalytics object.

#### `classifyPerformance(...)`
Simulates random forest ensemble classification.

#### `getHistoricalData(studentId)`
Retrieves historical performance for trend analysis.

### Integration
- Called in `StudentController.submitExam()`
- Results stored in `ExamSubmission` entity
- Displayed as radar chart and summary in `student-results.html`

---

## How They Work Together

### Exam Creation Flow
1. **PDF Upload** → `HomepageController.processExams()`
2. **Parse Questions** → `processFisherYates()`
3. **Shuffle Choices** → `FisherYatesService.shuffle()` ← Fisher-Yates
4. **Store Answer Key** → `session.setAttribute("correctAnswerKey")`

### Exam Taking Flow
1. **Student Receives Exam** → Shuffled choices displayed
2. **Submit Answers** → `StudentController.submitExam()`
3. **Grade Answers** → Compare with answer key
4. **Calculate Random Forest Analytics** → Performance metrics
5. **Calculate IRT 3PL Ability** → Theta estimation ← IRT 3PL
6. **Display Results** → Both analytics shown

### Results Display
```
┌─────────────────────────────────────┐
│ Your Final Score: 15/20 (75%)      │
├─────────────────────────────────────┤
│ Random Forest Analysis:             │
│  - Topic Mastery: 78%               │
│  - Difficulty Resilience: 72%       │
│  - Accuracy: 75%                    │
│  - Time Efficiency: 85%             │
│  - Confidence: 80%                  │
│  Performance: Good                  │
├─────────────────────────────────────┤
│ IRT 3PL Analysis:                   │
│  - Ability (θ): 0.523               │
│  - Scaled Score: 552/800            │
│  - Standard Error: 0.412            │
└─────────────────────────────────────┘
```

---

## Testing Each Algorithm

### Test Fisher-Yates
```java
@Autowired
private FisherYatesService fisherYatesService;

// Test distribution fairness
List<String> items = Arrays.asList("A", "B", "C", "D");
String stats = fisherYatesService.getShuffleStatistics(items, 10000);
System.out.println(stats);
```

Expected: Each item appears ~25% of the time in first position.

### Test IRT 3PL
```java
@Autowired
private IRT3PLService irt3PLService;

// Test ability estimation
List<Boolean> responses = Arrays.asList(true, true, false, true, true);
List<ItemParameters> params = irt3PLService.generateDefaultItemParameters(5);
AbilityEstimate estimate = irt3PLService.estimateAbility(responses, params);
System.out.println("Theta: " + estimate.getTheta());
System.out.println("Scaled: " + irt3PLService.thetaToScaledScore(estimate.getTheta(), 500, 100));
```

Expected: Theta between -4 and 4, scaled score around 200-800.

### Test Random Forest
```java
@Autowired
private RandomForestService randomForestService;

// Test analytics
List<String> answers = Arrays.asList("Paris", "London", "Berlin");
Map<Integer, String> key = Map.of(1, "Paris", 2, "Madrid", 3, "Berlin");
StudentAnalytics analytics = randomForestService.calculateStudentAnalytics("student1", answers, key, null);
System.out.println("Category: " + analytics.getPerformanceCategory());
```

Expected: Performance category based on scores.

---

## File Structure

```
src/main/java/
├── com/exam/
│   ├── service/
│   │   ├── IRT3PLService.java          ← IRT 3PL Implementation
│   │   ├── FisherYatesService.java     ← Fisher-Yates Implementation
│   │   └── AnswerKeyService.java
│   ├── Service/
│   │   └── RandomForestService.java    ← Random Forest Implementation
│   ├── Controller/
│   │   └── StudentController.java      ← Integrates all three
│   └── algo/
│       └── HomepageController.java     ← Uses Fisher-Yates & IRT
```

---

## References

### IRT 3PL
- Lord, F. M. (1980). *Applications of Item Response Theory*
- Hambleton, R. K., & Swaminathan, H. (1985). *Item Response Theory*
- Used in: SAT, GRE, TOEFL, and other standardized tests

### Fisher-Yates
- Fisher, R. A., & Yates, F. (1948). *Statistical Tables*
- Knuth, D. E. (1997). *The Art of Computer Programming, Vol. 2*
- Standard algorithm for unbiased shuffling

### Random Forest
- Breiman, L. (2001). *Random Forests, Machine Learning*
- Adapted for educational analytics and performance classification

---

## Future Enhancements

### IRT 3PL
- [ ] Calibrate item parameters from pilot data
- [ ] Implement Computerized Adaptive Testing (CAT)
- [ ] Add Bayesian estimation (EAP, MAP)
- [ ] Support 1PL (Rasch) and 2PL models

### Fisher-Yates
- [ ] Add option to shuffle question order with reindexing
- [ ] Implement deterministic shuffling with seed (for reproducibility)
- [ ] Add validation tests in test suite

### Random Forest
- [ ] Train actual Random Forest classifier with historical data
- [ ] Add more granular topic categorization
- [ ] Implement true time tracking per question
- [ ] Add learning trajectory prediction

---

## Summary

Each algorithm serves a specific purpose:

- **IRT 3PL**: Scientific ability measurement
- **Fisher-Yates**: Secure answer randomization  
- **Random Forest**: Comprehensive performance analytics

Together, they create a robust adaptive examination system that is both fair and informative.
