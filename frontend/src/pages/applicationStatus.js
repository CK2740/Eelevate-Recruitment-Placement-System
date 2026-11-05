const NEXT_STATUSES = {
  APPLIED: ["SHORTLISTED", "REJECTED"],
  SHORTLISTED: ["INTERVIEW", "REJECTED"],
  INTERVIEW: ["SELECTED", "REJECTED"],
  SELECTED: ["OFFERED", "REJECTED"],
  OFFERED: ["JOINED", "REJECTED"],
  JOINED: [],
  REJECTED: [],
};

export function nextApplicationStatuses(status) {
  return NEXT_STATUSES[status] || [];
}

export function applicationStatusRequiresReason(status) {
  return status === "SHORTLISTED" || status === "REJECTED";
}
