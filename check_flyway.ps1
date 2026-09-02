# Diagnostic script to check Flyway inconsistency
$dbHost = '100.114.133.69'
$dbPort = 30432
$dbUser = 'claimassist'
$dbPassword = 'sLZW4D2OBI09bp4qXQOj8NX5VFgS45kI'
$dbName = 'claimassist'

# Create PGPASSWORD environment variable to avoid password prompt
$env:PGPASSWORD = $dbPassword

Write-Host '=== QUERY 1: Current Database Identity ===' -ForegroundColor Green
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT current_database(), current_user, current_schema();"

Write-Host "`n=== QUERY 2: Flyway Schema History (ALL) ===" -ForegroundColor Green
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT installed_rank, version, description, type, script, checksum, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"

Write-Host "`n=== QUERY 3: Check outbox_events Columns ===" -ForegroundColor Green
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT table_schema, table_name, column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'outbox_events' ORDER BY ordinal_position;"

Write-Host "`n=== QUERY 4: Check request_id Column Specifically ===" -ForegroundColor Green
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'outbox_events' AND column_name = 'request_id';"

Write-Host "`n=== QUERY 5: Check if multiple outbox_events tables ===" -ForegroundColor Green
psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c "SELECT table_schema, table_name FROM information_schema.tables WHERE table_name = 'outbox_events';"

