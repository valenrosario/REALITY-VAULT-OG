// =====================================================================
// REALITY VAULT · conexión con Supabase
// Datos de Supabase → Project Settings → API.
// La "anon public key" es pública: está bien que esté en este archivo.
// NUNCA pongas acá la "service_role key".
// =====================================================================
window.RV_CONFIG = {
  supabaseUrl: 'https://iyfcbxehgahfoncqlxbt.supabase.co',
  supabaseAnonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml5ZmNieGVoZ2FoZm9uY3FseGJ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTU3NTg1NzQsImV4cCI6MjExMTMzNDU3NH0.HtopbseHK6SoeZNg6d7_5wlek7AC6DTU7gCMIPU6jdw',
  // Clave de TMDB para importar series y películas desde el panel
  tmdbKey: 'e1198bb10584cc859c3adb31cf9c8467'
};
