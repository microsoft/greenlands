param (
    $ClientVersion="1.0.0-LOCAL",

    # If PythonVersionOverride is provided, use it when generating the python package
    # Otherwise, use ClientVersion
    # 
    # There is an issue in with ADO package feeds which does only allows a limited version format for python packages. 
    # The typical version 1.0.0-CI-20220712.5 is not accepted, but 0.20220712.5 is accepted.
    # 
    # See: https://developercommunity.visualstudio.com/t/local-version-segments-for-python-package-feeds/892057
    $PythonVersionOverride="",
    [switch]$Install
)

function Write-Step {
    param (
        $Text
    )

    $separator = "=" * $Text.Length

    Write-Host ""
    Write-Host $separator -ForegroundColor Yellow
    Write-Host $Text -ForegroundColor Cyan
    Write-Host $separator -ForegroundColor Yellow
    Write-Host ""
}

if (-not $PythonVersionOverride) {
    $PythonVersionOverride=$ClientVersion
}

$clientGenerationDir=$PSScriptRoot

cd "$clientGenerationDir/.."

# Generate swagger file
$swaggerFileName = "swagger.json"

Write-Step "Generating $swaggerFileName"

cd Service
dotnet restore --locked-mode
dotnet build --no-restore
dotnet tool restore
dotnet tool list

cd Greenlands.Api
$apiKeyHeaderMatchResult = Get-Content ./Properties/launchSettings.json | Select-String -Pattern '"ApiKeyAuthentication:HeaderName": "([\w-]+)"'
$headerName = $apiKeyHeaderMatchResult.Matches[0].Groups[1].Value
echo "Set ApiKeyAuthentication__HeaderName to $headerName"
$env:ApiKeyAuthentication__HeaderName = $headerName

# TODO: Find how to get dll path from build instead of hard coding
# TODO: Find way to re-use swagger file later
# Note: We might generate the same swagger file twice! (Once here and possibly later in generate-client.ps1)
# Compare swagger file generated from target branch code to swagger file generated from current code
dotnet swagger tofile --output "$PSScriptRoot/$swaggerFileName" bin/Debug/net6.0/Greenlands.Api.dll v1

Write-Step "Downloading OpenAPI generator JAR"
cd $clientGenerationDir

if (-Not (Test-Path -Path "./downloads")) {
    mkdir downloads
}

if (-Not (Test-Path -Path "./downloads/openapi-generator-cli.jar")) {
    Invoke-WebRequest -OutFile downloads/openapi-generator-cli.jar https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/6.0.0/openapi-generator-cli-6.0.0.jar
}

if (Test-Path -Path "./JavaClient") {
    Write-Step "Existing JavaClient detected, deleting files"
    Remove-Item "./JavaClient" -Recurse -Force
}

Write-Step "Generating Java client v$ClientVersion"
java -jar downloads/openapi-generator-cli.jar generate -i swagger.json -g java -o JavaClient -c javaGenerationConfig.json -t templates/Java --global-property modelTests=false -p=artifactVersion="$ClientVersion"

Write-Step "Print Generated JavaClient/build.gradle"
type JavaClient/build.gradle

Write-Step "Overwrite generated JavaClient/build.gradle with custom build.gradle"
(Get-Content ./build.gradle.mustache) -replace '\{\{package_version\}\}',$ClientVersion | Set-Content ./JavaClient/build.gradle

Write-Step "Print Modified JavaClient/build.gradle"
type JavaClient/build.gradle

if (Test-Path -Path "./PythonClient") {
    Write-Step "Existing PythonClient detected, deleting files"
    Remove-Item "./PythonClient" -Recurse -Force
}

Write-Step "Generating PythonClient client $PythonVersionOverride"
java -jar downloads/openapi-generator-cli.jar generate -i swagger.json -g python -o PythonClient -c pythonGenerationConfig.json -p=packageVersion="$PythonVersionOverride"

if ($Install) {
    Write-Step "Install switch detected. Installing package dependencies and linking packages"

    cd JavaClient

    Write-Step "Publishing JavaClient to Maven Local repository"
    ./gradlew.bat publishToMavenLocal

    cd ..
    cd PythonClient

    Write-Step "Publishing Python package locally"
    pip install -e .

    cd ..
}

Write-Step "Done!"