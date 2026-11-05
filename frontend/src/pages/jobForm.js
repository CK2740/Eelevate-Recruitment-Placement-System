function textValue(form, name) {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function optionalNumber(form, name) {
  const value = textValue(form, name);
  return value === "" ? null : Number(value);
}

export function jobRequestFromForm(form) {
  return {
    companyId: Number(form.get("companyId")),
    jobTitle: textValue(form, "jobTitle"),
    technology: textValue(form, "technology"),
    openings: Number(form.get("openings")),
    location: textValue(form, "location"),
    packageAmount: Number(form.get("packageAmount")),
    jobDescription: textValue(form, "jobDescription") || null,
    skills: textValue(form, "skills") || null,
    requiredQualification: textValue(form, "requiredQualification") || null,
    applicationDeadline: textValue(form, "applicationDeadline"),
    employmentType: form.get("employmentType"),
    status: form.get("status"),
    eligibilityCriteria: {
      minimumPercentage: optionalNumber(form, "minimumPercentage"),
      minimumPassingYear: optionalNumber(form, "minimumPassingYear"),
      maximumPassingYear: optionalNumber(form, "maximumPassingYear"),
      maximumBacklogs: optionalNumber(form, "maximumBacklogs") ?? 0,
    },
  };
}

export function defaultJobStatus(record) {
  return record?.status || "OPEN";
}

export async function loadJobCompanies(api, record) {
  const result = await api.get("/companies", { status: "ACTIVE", size: 100 });
  const companies = result.content || [];
  if (record?.companyId && !companies.some((company) => company.companyId === record.companyId)) {
    companies.push({
      companyId: record.companyId,
      companyName: record.companyName,
    });
  }
  return companies;
}

export async function saveJob(api, record, request) {
  return record
    ? api.put(`/jobs/${record.jobId}`, request)
    : api.post("/jobs", request);
}

export function pageForSavedJob(totalElements, pageSize, jobs, savedJobId) {
  if (jobs.some((job) => job.jobId === savedJobId)) return null;
  return Math.max(0, Math.ceil(totalElements / pageSize) - 1);
}

export function jobSubmissionError(error) {
  const details = Object.entries(error?.details || {});
  if (!details.length) {
    return [error?.message || "Could not save the job. Please try again."];
  }
  return [
    error?.message || "Please correct the following job details:",
    ...details.map(([field, message]) => `${field}: ${message}`),
  ];
}
