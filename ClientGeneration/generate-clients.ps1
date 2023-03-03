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

if (-not $PythonVersionOverride) {
    $PythonVersionOverride=$ClientVersion
}

$clientGenerationDir=$PSScriptRoot

Import-Module "$PSScriptRoot/../pipelines/scripts/common.psm1" -Force

cd "$clientGenerationDir/.."

New-Swagger

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
    rm "./JavaClient" -Recurse -Force
}

Write-Step "Generating Java client v$ClientVersion"
java -jar downloads/openapi-generator-cli.jar generate -i swagger.json -g java -o JavaClient -c javaGenerationConfig.json -t templates/Java --global-property modelTests=false -p=artifactVersion="$ClientVersion"

Write-Step "Print Generated JavaClient/build.gradle"
type JavaClient/build.gradle

Write-Step "Overwrite generated JavaClient/build.gradle with custom build.gradle"
(Get-Content .\build.gradle.mustache) -replace '\{\{package_version\}\}',$ClientVersion | Set-Content ./JavaClient/build.gradle

Write-Step "Print Modified JavaClient/build.gradle"
type JavaClient/build.gradle

if (Test-Path -Path "./TypeScriptClient") {
    Write-Step "Existing TypeScriptClient detected, deleting files"
    Get-ChildItem -Path "./TypeScriptClient" -Exclude ".npmrc", "node_modules" | ForEach-Object {Remove-Item $_ -Recurse }
}

Write-Step "Generating TypeScript client $ClientVersion"
java -jar downloads/openapi-generator-cli.jar generate -i swagger.json -g typescript-fetch -o TypeScriptClient -c typescriptGenerationConfig.json -t templates/Typescript -p=npmVersion="$ClientVersion"

if (Test-Path -Path "./PythonClient") {
    Write-Step "Existing PythonClient detected, deleting files"
    rm "./PythonClient" -Recurse -Force
}

Write-Step "Generating PythonClient client $PythonVersionOverride"
java -jar downloads/openapi-generator-cli.jar generate -i swagger.json -g python -o PythonClient -c pythonGenerationConfig.json -p=packageVersion="$PythonVersionOverride"

if ($Install) {
    Write-Step "Install switch detected. Installing package dependencies and linking packages"

    cd JavaClient

    Write-Step "Publishing JavaClient to Maven Local repository"
    gradle publishToMavenLocal

    cd ..
    cd TypeScriptClient

    Write-Step "Installing TypeScript dependencies"
    npm i

    Write-Step "Linking TypeScript package"
    npm link

    cd ..
    cd PythonClient

    Write-Step "Publishing Python package locally"
    pip install -e .

    cd ..
}

Write-Step "Done!"