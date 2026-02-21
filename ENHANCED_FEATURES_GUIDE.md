# Enhanced Features Guide - ALGO Examination System

## New Features Overview

This document describes the new features added to the ALGO Examination System to improve teacher workflow and exam content capabilities.

---

## 1. 📸 Multi-Format Question Support (Images, Videos, Equations)

### Overview
The system now supports rich media content in exam questions, including:
- **Images** - Embed images directly in questions
- **Videos** - Include video content for multimedia assessments
- **Mathematical Equations** - LaTeX-style equation rendering using MathJax

### How to Use

#### Adding Images
```html
<img src="https://example.com/image.jpg" alt="Diagram description">
```

#### Adding Videos
```html
<video src="https://example.com/video.mp4" controls></video>
```

#### Adding Equations
- **Inline equations**: Use `\(equation\)` syntax
  - Example: `\(E = mc^2\)`
  
- **Display equations**: Use `\[equation\]` syntax
  - Example: `\[x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}\]`

#### Example Question with Mixed Content
```text
What is the area of the triangle shown below?

<img src="https://example.com/triangle.png" alt="Right triangle">

The formula for area is: \(A = \frac{1}{2}bh\)

A) 12 cm²
B) 24 cm²
C) 36 cm²
D) 48 cm²
```

### Important Notes
- Images and videos are loaded from URLs (ensure URLs are accessible)
- MathJax automatically renders equations on page load
- All question formats are supported: multiple choice, text input, and essay questions

---

## 2. 📤 Export Shuffled Exam with Answers

### Overview
Teachers can now export their uploaded and shuffled exams along with answer keys in multiple formats:
- **PDF** - Professional document format
- **CSV** - Spreadsheet format for easy data manipulation
- **DOCX** - Microsoft Word format for editing

### How to Export

1. Navigate to the **Manage Questions** page for any exam
2. At the top of the page, click the **"Export Exam with Answers"** dropdown button
3. Select your preferred format:
   - **Export as PDF** - Download formatted document
   - **Export as CSV** - Download spreadsheet
   - **Export as DOCX** - Download Word document

### What's Included in Exports

Each export includes:
- **Exam metadata** (name, subject, activity type, total questions)
- **All questions** in their shuffled order
- **Difficulty levels** for each question
- **Correct answers** for each question
- **Generation timestamp**

### Export Format Details

#### PDF Format
- Professional formatting with headers
- Questions numbered sequentially
- Color-coded difficulty indicators
- Answer key clearly marked

#### CSV Format
Columns:
- Question Number
- Question Text
- Difficulty
- Correct Answer

Perfect for:
- Importing into gradebooks
- Statistical analysis
- Database imports

#### DOCX Format
- Fully editable Word document
- Formatted text with bold/italic styling
- Color-coded answers (green)
- Easy to customize and print

---

## 3. 🧭 Top Navigation Bar

### Overview
A unified navigation bar is now present across all teacher pages for quick access to key sections.

### Navigation Items
- **Dashboard** - Main teacher homepage
- **Subjects** - Subject management page
- **Submissions** - Student submissions management
- **Results** - View all exam results
- **Grade** - Manual grading interface
- **Students** - Student list and statistics

### Features
- **Responsive design** - Collapses on mobile devices
- **Active indicators** - Current page highlighted
- **User info display** - Shows logged-in teacher email
- **Quick logout** - Logout button always accessible

---

## 4. 📚 Separate Subject Management Page

### Overview
Subject management has been moved to its own dedicated page for better organization.

### Access
Navigate to: `/teacher/subjects` or click "Subjects" in the top navigation bar

### Features

#### Subject Cards
- **Visual grid layout** - Easy to browse
- **Enrollment count** - See student numbers at a glance
- **Quick actions** - Open classroom, enroll students, delete

#### Create New Subject
- Click "Create New Subject" button
- Enter subject name and description
- Subject appears immediately in the grid

#### Subject Actions
Each subject card provides:
- **View Classroom** - Access subject-specific classroom
- **Enroll Students** - Add students to the subject
- **Delete Subject** - Remove subject and all enrollments

#### Enrollment Management
- View all enrolled students per subject
- Remove individual students from subjects
- Add multiple students to a subject

---

## 5. 📋 Separate Submissions Management Page

### Overview
Student submissions now have a dedicated management interface with advanced filtering and organization.

### Access
Navigate to: `/teacher/submissions` or click "Submissions" in the top navigation bar

### Features

#### Statistics Dashboard
Four key metrics displayed:
- **Total Submissions** - All submissions from enrolled students
- **Graded** - Submissions that have been graded
- **Pending** - Submissions awaiting grading
- **Released** - Results released to students

#### Advanced Filtering
Filter submissions by:
- **Exam Name** - Specific exam
- **Subject** - Subject category
- **Student Email** - Individual student
- **Status** - Graded, pending, or released

#### Submissions Table
Displays:
- Student email
- Exam name
- Subject (with badge)
- Submission date/time
- Score and percentage
- Performance category
- Grading status
- Release status

#### Quick Actions
For each submission:
- **View** - See detailed results
- **Analytics** - View Random Forest performance analytics
- **CSV** - Download individual result as CSV
- **Lock/Release** - Toggle result visibility for students

#### Bulk Operations
- **Download All Results** - Export all submissions as CSV
- **Grade Submissions** - Access manual grading interface

---

## 6. 🎨 Consistent Styling

### Overview
All pages now use the same CSS (`style.css`) for a consistent look and feel.

### Design Elements
- **EAC Color Scheme**
  - Maroon primary color (#7a1022)
  - Gold accents (#d4a32a)
  - Cream backgrounds (#f7f3ec)

- **Consistent Components**
  - Cards with soft shadows
  - Rounded corners (16px)
  - Hover effects on interactive elements
  - Bootstrap icons throughout

- **Responsive Design**
  - Mobile-friendly layouts
  - Collapsible navigation
  - Flexible grid systems

---

## Technical Implementation Notes

### New Controller Routes

```java
@GetMapping("/subjects")
// Displays subject management page

@GetMapping("/submissions")
// Displays submissions management page with filtering

@GetMapping("/export-exam-with-answers/pdf")
// Exports exam with answers as PDF

@GetMapping("/export-exam-with-answers/csv")
// Exports exam with answers as CSV

@GetMapping("/export-exam-with-answers/docx")
// Exports exam with answers as DOCX
```

### New Template Files
- `teacher-nav.html` - Navigation bar fragment
- `teacher-subjects.html` - Subject management page
- `teacher-submissions.html` - Submissions management page

### JavaScript Libraries Added
- **MathJax 3** - Mathematical equation rendering
- CDN: `https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js`

### HTML Rendering
- Changed from `th:text` to `th:utext` for question display
- Allows HTML content to render properly
- Sanitization should be considered for production use

---

## Best Practices

### For Images
- Use publicly accessible URLs (HTTPS recommended)
- Provide meaningful alt text for accessibility
- Keep image file sizes reasonable (< 1MB recommended)
- Test image loading before distributing exams

### For Videos
- Host videos on reliable platforms (YouTube, Vimeo, etc.)
- Include `controls` attribute for playback controls
- Consider video length (keep under 2-3 minutes for exam questions)
- Test video playback in different browsers

### For Equations
- Test equations with MathJax before finalizing
- Use standard LaTeX syntax
- For complex equations, use display mode `\[...\]`
- Preview equations in the Manage Questions page

### For Exports
- Export regularly as backups
- Use CSV for data analysis
- Use PDF for distribution
- Use DOCX for further editing

### For Navigation
- Use the top nav for quick access
- Familiarize yourself with all sections
- Bookmark frequently used pages

---

## Troubleshooting

### Images Not Loading
- Verify URL is accessible from browser
- Check image URL is using HTTPS
- Ensure URL doesn't require authentication

### Videos Not Playing
- Test video URL directly in browser
- Check video format compatibility (MP4 recommended)
- Verify video hosting allows embedding

### Equations Not Rendering
- Check LaTeX syntax is correct
- Ensure MathJax script loads (check browser console)
- Refresh page to trigger re-rendering

### Export Not Working
- Verify exam has questions
- Check browser allows downloads
- Try different export format

---

## Future Enhancements

Potential features for future releases:
- Image/video file upload (instead of URLs only)
- Equation editor GUI
- Bulk export of multiple exams
- Template library for common question types
- Audio support for listening comprehension questions

---

## Support

For questions or issues with these new features:
1. Check this guide first
2. Test in a different browser
3. Clear browser cache
4. Contact system administrator

---

**Last Updated:** February 21, 2026
**Version:** 2.0
**System:** ALGO Adaptive Examination System
