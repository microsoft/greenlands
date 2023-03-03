param (
  $DebugServerName
)

# load .env file if there's one
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

      [Environment]::SetEnvironmentVariable($key, $value, "Process") | Out-Null
    }
  }
}

Load-Env-File -FilePath "${PSScriptRoot}/../.env" -IsDefault true
Load-Env-File -FilePath "${PSScriptRoot}/../.env.local" -IsDefault false

# Start proxies
{{PROXY_START_COMMANDS}}
# Start servers
{{SERVER_START_COMMANDS}}