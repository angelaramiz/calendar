# cleanup.ps1 — Limpia todos los datos de usuario en Supabase
# Uso: .\cleanup.ps1

$SupabaseUrl = "https://ugtlxnrwfipoctckuvfd.supabase.co"
$AnonKey = "sb_publishable_KcdYZchjzzpizgM4nhTw8w_Bd6w6-d1"

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
        Write-Host "  $table" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "LIMPIEZA COMPLETADA" -ForegroundColor Green
