const experiences = Object.freeze({
  ADMIN: Object.freeze({
    heading: "System overview",
    subtitle: "Organization-wide recruitment performance, activity and operational follow-ups.",
    cards: [
      { label: "Total candidates", key: "totalCandidates", icon: "♙" },
      { label: "Companies", key: "totalCompanies", icon: "▦", color: "blue" },
      { label: "Active jobs", key: "openJobs", icon: "▤", color: "orange" },
      { label: "Applications", key: "totalApplications", icon: "☷", color: "purple" },
      { label: "Selected candidates", key: "selectedCandidates", icon: "✦", color: "blue" },
      { label: "Placements", key: "joinedPlacements", icon: "✓" },
      { label: "Placement rate", key: "placementPercentage", icon: "%", color: "orange", suffix: "%" },
    ],
    sections: ["funnel", "adminTasks", "audit"],
    actions: [
      { label: "Add candidate", href: "/candidate-form.html" },
      { label: "Add company", href: "/companies.html" },
      { label: "Create job", href: "/job-form.html" },
      { label: "View reports", href: "/reports.html" },
      { label: "Manage users", href: "/users.html" },
    ],
  }),
  RECRUITER: Object.freeze({
    heading: "Recruitment workspace",
    subtitle: "Your recruitment pipeline, open opportunities and upcoming candidate conversations.",
    cards: [
      { label: "Candidates in system", key: "totalCandidates", icon: "♙" },
      { label: "Active jobs", key: "openJobs", icon: "▤", color: "orange" },
      { label: "Your applications", key: "myApplications", icon: "☷", color: "purple" },
      { label: "Upcoming interviews", key: "upcomingInterviews", icon: "◷", color: "blue" },
    ],
    sections: ["upcomingInterviews", "recruitmentActivity", "placementTasks", "joiningProgress"],
    actions: [
      { label: "Add candidate", href: "/candidate-form.html" },
      { label: "Create job", href: "/job-form.html" },
      { label: "New application", href: "/applications.html" },
      { label: "Schedule interview", href: "/interviews.html" },
    ],
  }),
  CANDIDATE: Object.freeze({
    heading: "Candidate portal",
    subtitle: "Explore current opportunities and keep track of your next career step.",
    cards: [
      { label: "Profile completion", key: "candidateProfile", icon: "♙" },
      { label: "Available jobs", key: "availableJobs", icon: "▤", color: "orange" },
      { label: "My applications", key: "candidateApplications", icon: "☷", color: "purple" },
      { label: "My interviews", key: "candidateInterviews", icon: "◷", color: "blue" },
      { label: "My offers", key: "candidateOffers", icon: "◇" },
    ],
    sections: ["candidateActivity"],
    actions: [
      { label: "Browse open jobs", href: "/jobs.html" },
      { label: "Complete profile", href: "/candidate-profile.html" },
      { label: "Track applications", href: "/candidate-applications.html" },
    ],
  }),
});

export function dashboardExperienceFor(user) {
  const roles = user?.roles || [];
  if (roles.includes("ADMIN")) return experiences.ADMIN;
  if (roles.includes("RECRUITER")) return experiences.RECRUITER;
  if (roles.includes("CANDIDATE")) return experiences.CANDIDATE;
  return null;
}

export function dashboardExperience(role) {
  return experiences[role] || null;
}
