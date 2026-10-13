// =====================================================================
// REALITY VAULT · conexión con Supabase
// Datos de Supabase → Project Settings → API.
// La "anon public key" es pública: está bien que esté en este archivo.
// NUNCA pongas acá la "service_role key".
// =====================================================================
window.RV_CONFIG = {
  supabaseUrl: 'https://kfyxwoezrzboikbvpdtv.supabase.co',
  supabaseAnonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtmeXh3b2V6cnpib2lrYnZwZHR2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4NjI4MTksImV4cCI6MjEwNzQzODgxOX0.lQyvvIn1Lh_b6iULBvqaR18fSJnDHP6e__nk6RTJkhE',
  // Clave de TMDB para importar series y películas desde el panel
  tmdbKey: 'e1198bb10584cc859c3adb31cf9c8467'
};
