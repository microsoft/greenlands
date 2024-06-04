$host.ui.RawUI.WindowTitle = "{{SERVER_NAME}}"

function Load-Env-File () {
  param (
      $FilePath,
      $IsDefault
  )

  if (Test-Path $FilePath) {
    echo "Loading .env file: $FilePath"
    $content = Get-Content $FilePath -ErrorAction Stop

    foreach ($line in $content) {
      if([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        continue
      }


      $kvp = $line -split "=",2
      $key = $kvp[0].Trim()
      $value = $kvp[1].Trim()

      # If we're in the default .env file then never overwrite existing env variable values
      # They should only be overwritten by .env.local values
      if ($IsDefault -eq $true -and [System.Environment]::GetEnvironmentVariable($key) -ne $null) {
        return
      }

      echo "    Loaded env variable: $key"
      [Environment]::SetEnvironmentVariable($key, $value, "Process") | Out-Null
    }
  }
}

Load-Env-File -FilePath "/workspaces/greenlands/McPlugin/.env" -IsDefault true
Load-Env-File -FilePath "/workspaces/greenlands/McPlugin/.env.local" -IsDefault false

# No idea why --add-opens works. Got the "tip" from
# https://github.com/SmartDataAnalytics/jena-sparql-api/issues/43
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED  -Xmx{{SERVER_MEMORY}} -Xms{{SERVER_MEMORY}} -enableassertions -jar server.jar --nogui

Read-Host "Press ENTER to continue..."
