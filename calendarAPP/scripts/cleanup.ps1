# cleanup.ps1 — Limpia todos los datos de usuario en Supabase
# Uso: .\cleanup.ps1

$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co"
$AnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVndGx4bnJ3Zmlwb2N0Y2t1dmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzU4Mzk4MDQsImV4cCI6MjA1MTQxNTgwNH0.A5W4rRxYDxyPqFh7a4FX_ejniQl1nBNf1hMQuf7vjm4"

$headers = @{
    "apikey" = $AnonKey
    "Authorization" = "Bearer $AnonKey"
    "Content-Type" = "application/json"
    "Prefer" = "return=minimal"
}

$tables = @(
    "savings_transactions",
    "savings_pattern_income_sources",
    "savings_patterns",
    "plan_income_sources",
    "plans",
    "expense_pattern_income_sources",
    "movements",
    "income_patterns",
    "expense_patterns",
    "loans",
    "envelopes",
    "alerts",
    "expense_income_links",
    "financial_snapshots",
    "financial_recommendations",
    "expense_categories",
    "product_wishlist",
    "users"
)

Write-Host ""
Write-Host "LIMPIANDO DATOS..." -ForegroundColor Cyan

foreach ($table in $tables) {
    try {
        Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/$table?select=id" -Method DELETE -Headers $headers | Out-Null
        Write-Host "  $table OK" -ForegroundColor Green
    } catch {
        Write-Host "  $table ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "LIMPIEZA COMPLETADA" -ForegroundColor Green
