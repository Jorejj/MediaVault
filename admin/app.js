const els = {
  projectUrl: document.getElementById("projectUrl"),
  anonKey: document.getElementById("anonKey"),
  email: document.getElementById("email"),
  password: document.getElementById("password"),
  saveConfigBtn: document.getElementById("saveConfigBtn"),
  loginBtn: document.getElementById("loginBtn"),
  logoutBtn: document.getElementById("logoutBtn"),
  refreshBtn: document.getElementById("refreshBtn"),
  authStatus: document.getElementById("authStatus"),
  kpiUsers: document.getElementById("kpiUsers"),
  kpiMedia: document.getElementById("kpiMedia"),
  kpiEvents: document.getElementById("kpiEvents"),
  profilesOutput: document.getElementById("profilesOutput"),
  mediaOutput: document.getElementById("mediaOutput"),
};

const CFG_URL = "mediavault_admin_supabase_url";
const CFG_KEY = "mediavault_admin_supabase_anon_key";

els.projectUrl.value = localStorage.getItem(CFG_URL) || "";
els.anonKey.value = localStorage.getItem(CFG_KEY) || "";

let supabase = null;

function makeClient() {
  const url = (els.projectUrl.value || "").trim();
  const key = (els.anonKey.value || "").trim();
  if (!url || !key) {
    setStatus("Set Project URL and anon key first.");
    return null;
  }
  supabase = window.supabase.createClient(url, key);
  return supabase;
}

function setStatus(msg) {
  els.authStatus.textContent = msg;
}

async function ensureAdmin() {
  if (!supabase) return false;
  const { data: authData, error: authErr } = await supabase.auth.getUser();
  if (authErr || !authData?.user) {
    setStatus("Not logged in.");
    return false;
  }
  const { data: profile, error: profileErr } = await supabase
    .from("profiles")
    .select("id, email, role")
    .eq("id", authData.user.id)
    .single();
  if (profileErr) {
    setStatus("Cannot read profile. Check RLS/policies.");
    return false;
  }
  if (profile.role !== "admin") {
    setStatus("Logged in but not admin.");
    return false;
  }
  setStatus(`Admin: ${profile.email || authData.user.email}`);
  return true;
}

async function refreshDashboard() {
  if (!supabase) makeClient();
  if (!supabase) return;
  const ok = await ensureAdmin();
  if (!ok) return;

  const [{ count: usersCount }, { count: mediaCount }, { count: eventsCount }] = await Promise.all([
    supabase.from("profiles").select("*", { count: "exact", head: true }),
    supabase.from("media_library").select("*", { count: "exact", head: true }),
    supabase.from("user_media_events").select("*", { count: "exact", head: true }),
  ]);

  els.kpiUsers.textContent = usersCount ?? "0";
  els.kpiMedia.textContent = mediaCount ?? "0";
  els.kpiEvents.textContent = eventsCount ?? "0";

  const { data: profiles } = await supabase
    .from("profiles")
    .select("id, email, role, created_at")
    .order("created_at", { ascending: false })
    .limit(20);
  els.profilesOutput.textContent = JSON.stringify(profiles || [], null, 2);

  const { data: media } = await supabase
    .from("media_library")
    .select("id, user_id, local_media_id, title, media_type, status, last_updated")
    .order("last_updated", { ascending: false })
    .limit(30);
  els.mediaOutput.textContent = JSON.stringify(media || [], null, 2);
}

els.saveConfigBtn.addEventListener("click", () => {
  localStorage.setItem(CFG_URL, els.projectUrl.value.trim());
  localStorage.setItem(CFG_KEY, els.anonKey.value.trim());
  makeClient();
  setStatus("Config saved.");
});

els.loginBtn.addEventListener("click", async () => {
  if (!supabase) makeClient();
  if (!supabase) return;
  const email = (els.email.value || "").trim();
  const password = (els.password.value || "").trim();
  if (!email || !password) {
    setStatus("Email/password required.");
    return;
  }
  const { error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) {
    setStatus(`Login failed: ${error.message}`);
    return;
  }
  await refreshDashboard();
});

els.logoutBtn.addEventListener("click", async () => {
  if (!supabase) makeClient();
  if (!supabase) return;
  await supabase.auth.signOut();
  setStatus("Logged out.");
});

els.refreshBtn.addEventListener("click", refreshDashboard);
