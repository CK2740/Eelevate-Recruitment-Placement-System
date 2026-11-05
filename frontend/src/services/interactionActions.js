export async function runExclusively(locks, key, action) {
  if (locks.has(key)) return false;
  locks.add(key);
  try {
    await action();
    return true;
  } finally {
    locks.delete(key);
  }
}

export async function downloadReportCsv(api, type, filters, saveFile) {
  const file = await api.download(`/reports/${type}/csv`, filters);
  saveFile(file, `${type.toLowerCase()}.csv`);
}

export async function signOutAndClearUser(logout, clearUser) {
  await logout();
  clearUser();
}
