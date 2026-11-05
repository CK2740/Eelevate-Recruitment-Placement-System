export function isPendingRecruiter(user) {
  return user?.accountStatus === "PENDING_APPROVAL"
    && user.roles?.includes("RECRUITER") === true;
}

export async function reviewRecruiterRequest(api, userId, decision, confirm) {
  const action = decision === "APPROVE" ? "approve" : "reject";
  if (!confirm(`Are you sure you want to ${action} this recruiter account?`)) {
    return false;
  }
  await api.post(`/auth/admin/recruiters/${userId}/approval`, { decision });
  return true;
}
