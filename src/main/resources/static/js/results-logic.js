document.addEventListener('DOMContentLoaded', function() {
    
    // 1. Logic for Downloading the Processed Exam (Fisher-Yates/IRT)
    const downloadBtn = document.getElementById('downloadExamBtn');
    if (downloadBtn) {
        downloadBtn.addEventListener('click', function() {
            const data = document.getElementById('processedExamData').value;
            const blob = new Blob([data], { type: 'text/plain' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'Verified_Final_Exam.txt';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        });
    }
	function viewOnline() {
	    const viewer = document.getElementById('onlineViewer');
	    const list = document.getElementById('questionList');
	    
	    // In a real app, you'd fetch the session data via an AJAX call
	    // For this example, we'll assume the data is passed or we just toggle visibility
	    viewer.classList.toggle('hidden');
	    
	    if (!viewer.classList.contains('hidden')) {
	        // You can use a fetch() call here to get the JSON list from the backend
	        console.log("Teacher is viewing the shuffled exam online.");
	    }
	}
    // 2. Logic for Rendering the Performance Radar Chart (Random Forest)
    const radarBtn = document.getElementById('renderRadarBtn');
    if (radarBtn) {
        radarBtn.addEventListener('click', function() {
            // Reveal the chart section
            document.getElementById('chartSection').classList.remove('hidden');
            
            // Get the scores calculated by your controller's formulas [cite: 287, 297]
            const tmScore = document.getElementById('tmScoreValue').value || 33;
            const drScore = document.getElementById('drScoreValue').value || 50;
            
            const ctx = document.getElementById('radarChartCanvas').getContext('2d');
            new Chart(ctx, {
                type: 'radar',
                data: {
                    labels: ['Topic Mastery', 'Difficulty Resilience', 'Accuracy', 'Time Efficiency', 'Confidence'],
                    datasets: [{
                        label: 'Student Analytics Profile',
                        data: [tmScore, drScore, 80, 70, 75], // TM and DR are computed from raw data [cite: 74]
                        fill: true,
                        backgroundColor: 'rgba(25, 135, 84, 0.2)',
                        borderColor: 'rgb(25, 135, 84)',
                        pointBackgroundColor: 'rgb(25, 135, 84)',
                        pointBorderColor: '#fff',
                    }]
                },
                options: {
                    elements: { line: { borderWidth: 3 } },
                    scales: {
                        r: {
                            angleLines: { display: true },
                            suggestedMin: 0,
                            suggestedMax: 100
                        }
                    }
                }
            });
            
            // Scroll to the chart and disable button
            window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
            radarBtn.disabled = true;
        });
    }
});